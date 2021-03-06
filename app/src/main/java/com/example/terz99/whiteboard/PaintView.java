package com.example.terz99.whiteboard;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.provider.Settings.Secure;

import com.firebase.client.ChildEventListener;
import com.firebase.client.DataSnapshot;
import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;
import com.firebase.client.ValueEventListener;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Objects;
import java.util.Random;

/**
 * PaintView. Mostly interacts with the database and contains the drawing algorithm. The drawing
 * algorithm was referenced from here: https://medium.com/@ssaurel/learn-to-create-a-paint-application-for-android-5b16968063f8
 * We mainly focused on the Realtime data transfer instead of the quality of the writing so please don't
 * mind if there are glitches when drawing.
 */
public class PaintView extends View {

    @SuppressLint("HardwareIds")
    private final String id = Secure.getString(getContext().getContentResolver(), Secure.ANDROID_ID);
    private String boardId = null;
    private boolean firstTimeDraw = true;
    private final String LOG_TAG = this.getClass().getName();
    private Firebase dbReference;
    private ArrayList<Coordinates> coord;
    private HashSet<String> refKeys;
    public static int BRUSH_SIZE = 10;
    public static final int DEFAULT_COLOR = Color.RED;
    public static final int DEFAULT_BG_COLOR = Color.WHITE;
    private static final float TOUCH_TOLERANCE = 4;
    private float mX, mY;
    private Path mPath;
    private Paint mPaint;
    private ArrayList<FingerPath> paths = new ArrayList<>();
    private int currentColor;
    private int backgroundColor = DEFAULT_BG_COLOR;
    private Bitmap mBitmap;
    private Canvas mCanvas;
    private Paint mBitmapPaint = new Paint(Paint.DITHER_FLAG);
    public PaintView(Context context) {
        this(context, null);
    }

    @SuppressLint("HardwareIds")
    public PaintView(Context context, AttributeSet attrs) {
        super(context, attrs);
        refKeys = new HashSet<String>();
        dbReference = new Firebase("https://share-n-draw.firebaseio.com/");
        coord = new ArrayList<Coordinates>();
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setDither(true);
        mPaint.setColor(DEFAULT_COLOR);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeJoin(Paint.Join.ROUND);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setXfermode(null);
        mPaint.setAlpha(0xff);
    }

    private void simulateDrawing(ArrayList<Coordinates> newCoords) {
        for(Coordinates event : newCoords){
            switch (event.getAction()){
                case MotionEvent.ACTION_DOWN :
                    touchStart(event.getX(), event.getY(), false);
                    invalidate();
                    break;
                case MotionEvent.ACTION_MOVE :
                    touchMove(event.getX(), event.getY(), false);
                    invalidate();
                    break;
                case MotionEvent.ACTION_UP :
                    touchUp(event.getX(), event.getY(), false);
                    invalidate();
                    break;
            }
        }
    }

    public void init(DisplayMetrics metrics, String boardId){
        int height = metrics.heightPixels;
        int width = metrics.widthPixels;
        this.boardId = boardId;
        dbReference.child(boardId).addValueEventListener(new ValueEventListener() {
            @SuppressLint("NewApi")
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                ArrayList<Coordinates> newCoords = new ArrayList<Coordinates>();
                for(DataSnapshot coordSnapshot : dataSnapshot.getChildren()){
                    Coordinates currCoord = coordSnapshot.getValue(Coordinates.class);
                    if(!Objects.equals(currCoord.getId(), id)) {
                        newCoords.add(currCoord);
                    }
                    refKeys.add(coordSnapshot.getKey());
                }
                simulateDrawing(newCoords);
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {

            }
        });
        dbReference.child(boardId).addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                ArrayList<Coordinates> newCoord = new ArrayList<Coordinates>();
                Coordinates currCoord = dataSnapshot.getValue(Coordinates.class);
                newCoord.add(currCoord);
                refKeys.add(dataSnapshot.getKey());
                simulateDrawing(newCoord);
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) {

            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {
                clear();
            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String s) {

            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {
                Log.e(LOG_TAG, "Could not load lines.");
            }
        });
//        dbReference.child(boardId).addValueEventListener(new ValueEventListener() {
//            @SuppressLint("NewApi")
//            @Override
//            public void onDataChange(DataSnapshot dataSnapshot) {
//                ArrayList<Coordinates> newCoord = new ArrayList<Coordinates>();
//                for(DataSnapshot coordSnapshot : dataSnapshot.getChildren()){
//                    Coordinates currCoord = coordSnapshot.getValue(Coordinates.class);
//                    if(!refKeys.contains(coordSnapshot.getKey()) && !Objects.equals(currCoord.getId(), id)){
//                        newCoord.add(currCoord);
//                    }
//                }
//                simulateDrawing(newCoord);
//            }
//
//            @Override
//            public void onCancelled(FirebaseError firebaseError) {
//                Log.e(LOG_TAG, "Could not load lines.");
//            }
//        });

        mBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        mCanvas = new Canvas(mBitmap);

        currentColor = DEFAULT_COLOR;
    }

    public void clear(){
        backgroundColor = DEFAULT_BG_COLOR;
        paths.clear();
        for(String refKey : refKeys){
            if(!refKey.equals("dummy")) {
                dbReference.child(boardId).child(refKey).removeValue();
            }
        }
        refKeys.clear();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas){
        canvas.save();
        mCanvas.drawColor(backgroundColor);

        for(FingerPath fp : paths){
            mPaint.setColor(fp.color);
            mPaint.setMaskFilter(null);
            mPaint.setStrokeWidth((float) 3.0);

            mCanvas.drawPath(fp.path, mPaint);
        }
        canvas.drawBitmap(mBitmap, 0,0, mBitmapPaint);
        canvas.restore();
    }

    private void touchStart(float x, float y, boolean addToCoord){
        mPath = new Path();
        FingerPath fp = new FingerPath(currentColor, mPath);
        paths.add(fp);

        mPath.reset();
        mPath.moveTo(x, y);
        mX = x;
        mY = y;
        if(addToCoord) {
            coord.add(new Coordinates(x, y, this.id, MotionEvent.ACTION_DOWN));
        }
    }

    private void touchMove(float x, float y, boolean addToCoord){
        float dx = Math.abs(x - mX);
        float dy = Math.abs(y - mY);

        if(dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE){
            mPath.quadTo(mX, mY, (x + mX) /2, (y + mY) /2);
            mX = x;
            mY = y;
            if(addToCoord) {
                coord.add(new Coordinates(x, y, this.id, MotionEvent.ACTION_MOVE));
            }
        }
    }

    private String getNextString(){
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append((new Date()).getTime());
        Random random = new Random();
        for(int i = 0; i < 32; i++){
            int c = random.nextInt(26) + 97;
            stringBuilder.append((char)c);
        }
        return stringBuilder.toString();
    }

    private void touchUp(float x, float y, boolean addToCoord){
        mPath.lineTo(x, y);
        if(addToCoord) {
            coord.add(new Coordinates(x, y, this.id, MotionEvent.ACTION_UP));
        }
        for(Coordinates c : coord){
            String nextStringIdx = getNextString();
            dbReference.child(boardId).child(nextStringIdx).child("x").setValue(c.getX());
            dbReference.child(boardId).child(nextStringIdx).child("y").setValue(c.getY());
            dbReference.child(boardId).child(nextStringIdx).child("action").setValue(c.getAction());
            dbReference.child(boardId).child(nextStringIdx).child("id").setValue(id);
        }
        coord.clear();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event){
        float x = event.getX();
        float y = event.getY();

        switch(event.getAction()){
            case MotionEvent.ACTION_DOWN :
                touchStart(x, y, true);
                invalidate();
                break;
            case MotionEvent.ACTION_MOVE :
                touchMove(x, y, true);
                invalidate();
                break;
            case MotionEvent.ACTION_UP :
                touchUp(x, y, true);
                invalidate();
                break;
        }
        return true;
    }
}
