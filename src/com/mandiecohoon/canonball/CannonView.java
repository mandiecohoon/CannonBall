package com.mandiecohoon.canonball;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.google.common.primitives.Ints;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class CannonView extends SurfaceView implements SurfaceHolder.Callback {
   private static final String TAG = "CannonView";

   private CannonThread cannonThread;
   private Activity activity;
   private boolean dialogIsDisplayed = false;   
               
   //public static final int TARGET_PIECES = 1; 
   public static final int MISS_PENALTY = 2;
   public static final int HIT_REWARD = 3; 

   private boolean gameOver; 
   private double timeLeft;
   private int shotsFired; 
   private double totalElapsedTime; 

   private Line blocker; 
   private int blockerDistance; 
   private int blockerBeginning;
   private int blockerEnd;
   private int initialBlockerVelocity;
   private float blockerVelocity;

   private Line target; 
   private int targetDistance;
   private int targetBeginning; 
   private double pieceLength; 
   private int targetEnd; 
   private int initialTargetVelocity; 
   private float targetVelocity; 

   private int lineWidth;
   private boolean[] hitStates;
   private int targetPiecesHit; 

   private Point cannonball; 
   private int cannonballVelocityX; 
   private int cannonballVelocityY; 
   private boolean cannonballOnScreen; 
   private int cannonballRadius; 
   private int cannonballSpeed; 
   private int cannonBaseRadius;
   private int cannonLength;
   private Point barrelEnd; 
   private int screenWidth; 
   private int screenHeight; 

   private static final int TARGET_SOUND_ID = 0;
   private static final int CANNON_SOUND_ID = 1;
   private static final int BLOCKER_SOUND_ID = 2;
   private SoundPool soundPool;
   private SparseIntArray soundMap;

   private Paint textPaint;
   private Paint cannonballPaint;
   private Paint cannonPaint;
   private Paint blockerPaint; 
   private Paint targetPaint;
   private Paint backgroundPaint;
   
   private int level = 1;
   private int TARGET_PIECES = 1;
   private int score = 0;
   private int[] highscores = new int[6];
   
   public void addLevel() {
	   level++;
	   TARGET_PIECES = level;
	   pieceLength = (targetEnd - targetBeginning) / TARGET_PIECES;
	   hitStates = new boolean[TARGET_PIECES];
	   initialBlockerVelocity += 80;
   }
   
   public CannonView(Context context, AttributeSet attrs) {
      super(context, attrs); 
      activity = (Activity) context;
      
      getHolder().addCallback(this); 

      blocker = new Line();
      target = new Line();
      cannonball = new Point(); 

      hitStates = new boolean[TARGET_PIECES];

      soundPool = new SoundPool(1, AudioManager.STREAM_MUSIC, 0);

      soundMap = new SparseIntArray(3);
      soundMap.put(TARGET_SOUND_ID, soundPool.load(context, R.raw.target_hit, 1));
      soundMap.put(CANNON_SOUND_ID, soundPool.load(context, R.raw.cannon_fire, 1));
      soundMap.put(BLOCKER_SOUND_ID, soundPool.load(context, R.raw.blocker_hit, 1));

      textPaint = new Paint(); 
      cannonPaint = new Paint(); 
      cannonballPaint = new Paint();
      blockerPaint = new Paint();  
      targetPaint = new Paint(); 
      backgroundPaint = new Paint(); 
   }

   @Override
   protected void onSizeChanged(int w, int h, int oldw, int oldh) {
      super.onSizeChanged(w, h, oldw, oldh);

      screenWidth = w; 
      screenHeight = h; 
      cannonBaseRadius = h / 18; 
      cannonLength = w / 8; 

      cannonballRadius = w / 36; 
      cannonballSpeed = w * 3 / 2;

      lineWidth = w / 24; 

      blockerDistance = w * 5 / 8; 
      blockerBeginning = h / 8;
      blockerEnd = h * 3 / 8; 
      initialBlockerVelocity = h / 2; 
      blocker.start = new Point(blockerDistance, blockerBeginning);
      blocker.end = new Point(blockerDistance, blockerEnd);

      targetDistance = w * 7 / 8; 
      targetBeginning = h / 8; 
      targetEnd = h * 7 / 8; 
      pieceLength = (targetEnd - targetBeginning) / TARGET_PIECES;
      initialTargetVelocity = -h / 4;
      target.start = new Point(targetDistance, targetBeginning);
      target.end = new Point(targetDistance, targetEnd);

      barrelEnd = new Point(cannonLength, h / 2);

      textPaint.setTextSize(w / 20); 
      textPaint.setAntiAlias(true);
      cannonPaint.setStrokeWidth(lineWidth * 1.5f); 
      blockerPaint.setStrokeWidth(lineWidth);
      targetPaint.setStrokeWidth(lineWidth);        
      backgroundPaint.setColor(Color.WHITE); 

      newGame();
   }

   public void newGame() {
      for (int i = 0; i < TARGET_PIECES; i++)
         hitStates[i] = false;

      targetPiecesHit = 0;
      blockerVelocity = initialBlockerVelocity;
      targetVelocity = initialTargetVelocity; 
      timeLeft = 10; 
      cannonballOnScreen = false; 
      shotsFired = 0;
      totalElapsedTime = 0.0; 
      
      blocker.start.set(blockerDistance, blockerBeginning);
      blocker.end.set(blockerDistance, blockerEnd);
      target.start.set(targetDistance, targetBeginning);
      target.end.set(targetDistance, targetEnd);
      
      if (gameOver) {
         gameOver = false;
         cannonThread = new CannonThread(getHolder());
         cannonThread.start();
      } 
   } 

   private void updatePositions(double elapsedTimeMS) {
      double interval = elapsedTimeMS / 1000.0;

      if (cannonballOnScreen) {
         cannonball.x += interval * cannonballVelocityX;
         cannonball.y += interval * cannonballVelocityY;

         if (cannonball.x + cannonballRadius > blockerDistance && 
            cannonball.x - cannonballRadius < blockerDistance &&
            cannonball.y + cannonballRadius > blocker.start.y &&
            cannonball.y - cannonballRadius < blocker.end.y) {
            cannonballVelocityX *= -1; 
            timeLeft -= MISS_PENALTY; 
            score -= (level * 15);

            soundPool.play(soundMap.get(BLOCKER_SOUND_ID), 1, 1, 1, 0, 1f);
         
         } else if (cannonball.x + cannonballRadius > screenWidth || cannonball.x - cannonballRadius < 0) {
            cannonballOnScreen = false;
         } else if (cannonball.y + cannonballRadius > screenHeight ||cannonball.y - cannonballRadius < 0) {
            cannonballOnScreen = false; 
         } else if (cannonball.x + cannonballRadius > targetDistance && 
            cannonball.x - cannonballRadius < targetDistance && 
            cannonball.y + cannonballRadius > target.start.y &&
            cannonball.y - cannonballRadius < target.end.y) { 
            int section = (int) ((cannonball.y - target.start.y) / pieceLength);
            
            if ((section >= 0 && section < TARGET_PIECES) && !hitStates[section]) {
               hitStates[section] = true;  
               cannonballOnScreen = false;  
               timeLeft += HIT_REWARD;
               score += (level * 10);
 
               soundPool.play(soundMap.get(TARGET_SOUND_ID), 1, 1, 1, 0, 1f);
 
               if (++targetPiecesHit == TARGET_PIECES) {
                  cannonThread.setRunning(false);
                  showGameOverDialog(R.string.win, score); 
                  gameOver = true;
                  if(level > 9) {
                  	level = 0;
                  	initialBlockerVelocity -= (80 * level);
                  }
               } 
            }
         }
      }
 
      double blockerUpdate = interval * blockerVelocity;
      blocker.start.y += blockerUpdate;
      blocker.end.y += blockerUpdate;

      double targetUpdate = interval * targetVelocity;
      target.start.y += targetUpdate;
      target.end.y += targetUpdate;

      if (blocker.start.y < 0 || blocker.end.y > screenHeight)blockerVelocity *= -1;
      
      if (target.start.y < 0 || target.end.y > screenHeight)targetVelocity *= -1;

      timeLeft -= interval;

      if (timeLeft <= 0.0) {
         timeLeft = 0.0;
         gameOver = true;
         initialBlockerVelocity -= (80 * level);
         insertHighscores(score);
         level = 0;
         cannonThread.setRunning(false);
         showGameOverDialog(R.string.lose, score);
         score = 0;
      } 
   }

   public void fireCannonball(MotionEvent event) {
      if (cannonballOnScreen)
         return;

      double angle = alignCannon(event);

      cannonball.x = cannonballRadius; 
      cannonball.y = screenHeight / 2; 

      cannonballVelocityX = (int) (cannonballSpeed * Math.sin(angle));

      cannonballVelocityY = (int) (-cannonballSpeed * Math.cos(angle));
      cannonballOnScreen = true; 
      ++shotsFired; 

      soundPool.play(soundMap.get(CANNON_SOUND_ID), 1, 1, 1, 0, 1f);
   } 

   public double alignCannon(MotionEvent event) {
      Point touchPoint = new Point((int) event.getX(), (int) event.getY());

      double centerMinusY = (screenHeight / 2 - touchPoint.y);

      double angle = 0;

      if (centerMinusY != 0) angle = Math.atan((double) touchPoint.x / centerMinusY);

      if (touchPoint.y > screenHeight / 2) angle += Math.PI;

      barrelEnd.x = (int) (cannonLength * Math.sin(angle));
      barrelEnd.y = (int) (-cannonLength * Math.cos(angle) + screenHeight / 2);

      return angle;
   } 

   public void drawGameElements(Canvas canvas) {
      canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), backgroundPaint);
      
      canvas.drawText(getResources().getString(R.string.time_remaining_format, timeLeft), 30, 50, textPaint);
      canvas.drawText(getResources().getString(R.string.score_format, score), 475, 50, textPaint);

      if (cannonballOnScreen) canvas.drawCircle(cannonball.x, cannonball.y, cannonballRadius, cannonballPaint);

      canvas.drawLine(0, screenHeight / 2, barrelEnd.x, barrelEnd.y, cannonPaint);

      canvas.drawCircle(0, (int) screenHeight / 2, (int) cannonBaseRadius, cannonPaint);

      canvas.drawLine(blocker.start.x, blocker.start.y, blocker.end.x, blocker.end.y, blockerPaint);

      Point currentPoint = new Point(); 

      currentPoint.x = target.start.x;
      currentPoint.y = target.start.y;
      
      for (int i = 0; i < TARGET_PIECES; i++) {
         if (!hitStates[i]) {
            if (i % 2 != 0)
               targetPaint.setColor(Color.GRAY);
            else
               targetPaint.setColor(Color.GREEN);
            
            canvas.drawLine(currentPoint.x, currentPoint.y, target.end.x, (int) (currentPoint.y + pieceLength), targetPaint);
         } 
         
         currentPoint.y += pieceLength;
      } 
   }

   private void showGameOverDialog(final int messageId, final int gameScore) {
      final DialogFragment gameResult = new DialogFragment() {
            @Override
            public Dialog onCreateDialog(Bundle bundle) {
               AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
               builder.setTitle(getResources().getString(messageId));
               
               SharedPreferences savedHighscores = activity.getSharedPreferences("pref", Context.MODE_PRIVATE);
               
               String score1 = "" + savedHighscores.getInt("highscore0", 0);
               String score2 = "" + savedHighscores.getInt("highscore1", 0);
               String score3 = "" + savedHighscores.getInt("highscore2", 0);
               String score4 = "" + savedHighscores.getInt("highscore3", 0);
               String score5 = "" + savedHighscores.getInt("highscore4", 0);
               
              if (score1.equals(String.valueOf(gameScore)))
            	  score1 += " (*)";
              if (score2.equals(String.valueOf(gameScore)))
            	  score2 += " (*)";
              if (score3.equals(String.valueOf(gameScore)))
            	  score3 += " (*)";
              if (score4.equals(String.valueOf(gameScore)))
            	  score4 += " (*)";
              if (score5.equals(String.valueOf(gameScore)))
            	  score5 += " (*)";
               
               builder.setMessage(getResources().getString(R.string.results_format, shotsFired, totalElapsedTime, gameScore) +"\n\n"
            		   + "High Scores:\n1. " 
		               + score1
		               + "\n2. "
		               + score2
		               + "\n3. "
		               + score3
		               + "\n4. "
		               + score4
		               + "\n5. "
		               + score5
            	);
               builder.setPositiveButton(R.string.next, new DialogInterface.OnClickListener() {
                     @Override
                     public void onClick(DialogInterface dialog, int which) {
                        dialogIsDisplayed = false;
                        addLevel();
                        newGame();
                     } 
                  } 
               );
               
               return builder.create(); 
            }    
         };
      
      activity.runOnUiThread(
         new Runnable() {
            public void run() {
               dialogIsDisplayed = true; 
               gameResult.setCancelable(false);
               gameResult.show(activity.getFragmentManager(), "results");
            } 
         }
      );
   }
   
   private void insertHighscores(int currentScore) {
	   SharedPreferences savedHighscores = activity.getSharedPreferences("pref", Context.MODE_PRIVATE);
	   SharedPreferences.Editor preferencesEditor = savedHighscores.edit();
	   
	   for (int i = 0; i < 5; i++) {
		   highscores[i] = savedHighscores.getInt("highscore" + String.valueOf(i), 0);
	   }
	   
	   highscores[5] = currentScore;
	   
	    List<Integer>list = Ints.asList(highscores);
	    Collections.sort(list, comparator);
	    highscores = Ints.toArray(list);
	   
	   for (int i = 0; i < 5; i++) {
		   preferencesEditor.putInt("highscore" + String.valueOf(i), highscores[i]);
	   }
	   
	   preferencesEditor.apply(); 
	  
   }
   
   Comparator<Integer> comparator = new Comparator<Integer>() {

       @Override
       public int compare(Integer o1, Integer o2) {
           return o2.compareTo(o1);
       }
   };
   
   public void stopGame() {
      if (cannonThread != null)
         cannonThread.setRunning(false);
   } 

   public void releaseResources() {
      soundPool.release();
      soundPool = null; 
   }

   @Override
   public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
   } 

   @Override
   public void surfaceCreated(SurfaceHolder holder) {
      if (!dialogIsDisplayed) {
         cannonThread = new CannonThread(holder);
         cannonThread.setRunning(true);
         cannonThread.start();
      } 
   } 

   @Override
   public void surfaceDestroyed(SurfaceHolder holder) {
      boolean retry = true;
      cannonThread.setRunning(false);
      
      while (retry) {
         try {
            cannonThread.join();
            retry = false;
         } catch (InterruptedException e) {
            Log.e(TAG, "Thread interrupted", e);
         } 
      }
   }
   
   @Override
   public boolean onTouchEvent(MotionEvent e) {
      int action = e.getAction();

      if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
         fireCannonball(e);
      } 

      return true;
   }
   
   private class CannonThread extends Thread {
      private SurfaceHolder surfaceHolder;
      private boolean threadIsRunning = true;
      
      public CannonThread(SurfaceHolder holder) {
         surfaceHolder = holder;
         setName("CannonThread");
      } 
      
      public void setRunning(boolean running) {
         threadIsRunning = running;
      } 
      
      @Override
      public void run() {
         Canvas canvas = null;
         long previousFrameTime = System.currentTimeMillis(); 
        
         while (threadIsRunning) {
            try {
               canvas = surfaceHolder.lockCanvas(null);               
               
               synchronized(surfaceHolder) {
                  long currentTime = System.currentTimeMillis();
                  double elapsedTimeMS = currentTime - previousFrameTime;
                  totalElapsedTime += elapsedTimeMS / 1000.0; 
                  updatePositions(elapsedTimeMS);
                  drawGameElements(canvas); 
                  previousFrameTime = currentTime; 
               } 
            } finally {
               if (canvas != null) 
                  surfaceHolder.unlockCanvasAndPost(canvas);
               
            }
         } 
      }
   }
}