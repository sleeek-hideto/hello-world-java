package jp.hidetobara.pasteltouch;


import java.util.LinkedList;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.FloatMath;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;


public class CanvasView extends SurfaceView implements SurfaceHolder.Callback
{
	final float SCALE_MIN = 0.5f;
	final float SCALE_MAX = 5.0f;
	
	private static final int NONE = 0;
	private static final int DRAW = 1;
	private static final int MOVE = 2;
	private int _Mode = 0;
	
	public static final int SURFACE_FLAT = 0;
	public static final int SURFACE_DITHER = 1;
	public static final int SURFACE_RANDOM = 2;
	private int _Surface = 2;
	public void setSurface(int s){ _Surface = s; }
	
	private int _Height, _Width;
	private Bitmap _Bitmap;
	private int[] _Buffer;
	private int _EdgeLength = 5;
	private int _MainColor;
	
	private ActQueue _ActQueue = new ActQueue();	
	private BitmapStack _HistoryBitmap;
	
	private Handler _Handler = new Handler();
	private Thread _Thread;
	private SurfaceHolder _Holder;
	private boolean _ThreadIsRefreshing = false;
	
	private Matrix _CanvasMatrix = new Matrix();;
    private float[] _CanvasMatrixValues = new float[9];
	private PointPair _MoveStartHand;
	private Matrix _MoveStartMatrix = new Matrix();
	private LinkedList<PointPair> _HistoryHand = new LinkedList<CanvasView.PointPair>();
	private int _HistoryEdgesMax = 3;
	
	private SlideValue _BlotCurrent = new SlideValue(0, 64, 4);	//キャンバスの絵の具
	private SlideValue _RemainCurrent = new SlideValue(0, 192, 4);	//筆のついた絵の具
	private SlideValue _BaseCurrent = new SlideValue(64, 64, 2);
	public void setBlot(int min, int max, int gain){ _BlotCurrent = new SlideValue(min, max, gain); }
	public void setRemain(int min, int max, int gain){ _RemainCurrent = new SlideValue(min, max, gain); }
	public void setBase(int b){ _BaseCurrent = new SlideValue(b, b, 2); }
	
	public CanvasView(Context context){ super(context); initialize(); }
	public CanvasView(Context context, AttributeSet attrs){ super(context, attrs); initialize(); }
	public CanvasView(Context context, AttributeSet attrs, int defStyle){ super(context, attrs, defStyle); initialize(); }

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
	{	
	}
	
	@Override
	public void surfaceCreated(SurfaceHolder holder)
	{
		doDraw();
	}
	
	@Override
	public void surfaceDestroyed(SurfaceHolder holder)
	{
	}
	
	/*
	 * 最初に一回だけ呼ばれる
	 */
	private void initialize()
	{		
		_Holder = getHolder();
		_Holder.addCallback(this);
		
		setFocusable(true);
		requestFocus();
		
		_Thread = new Thread(new DrawingBitmapTask());
		_Thread.start();
		
		_HistoryBitmap = new BitmapStack(9);
	}
	
	public void refreshWorking()
	{
		_HistoryBitmap.clear();
		_ActQueue.clear();
		_ThreadIsRefreshing = true;
	}
	
	/*
	 * キャンバスを白紙にした時などに
	 */
	public void reinitialize()
	{
		refreshWorking();
		pushHistoryBitmap();		
	}
	
	/*
	 * View に描き込み
	 */
	public void doDraw()
	{
		if( _Bitmap == null || _Holder == null ) return;
		Canvas canvas = _Holder.lockCanvas();
		if( canvas == null ) return;
		
		_Bitmap.setPixels(_Buffer, 0, _Width, 0, 0, _Width, _Height);
		canvas.drawColor(Color.DKGRAY);
		canvas.drawBitmap(_Bitmap, _CanvasMatrix, null);

		LinkedList<ActQueue.ActLine> lines = _ActQueue.getWholeLines();
		if(lines != null)
		{
			for(ActQueue.ActLine line : lines) drawTemporaryLine(canvas, line);
		}
		
		_Holder.unlockCanvasAndPost(canvas);
	}
	
	/*
	 * 仮の線を描く
	 */
	private void drawTemporaryLine(Canvas canvas, ActQueue.ActLine line)
	{
		float[] points = line.getFloatArray();
		if(points == null || points.length == 0) return;

		_CanvasMatrix.mapPoints(points);	//投影

		int count = points.length/2;
		Path path = new Path();
		path.moveTo(points[0], points[1]);
		for(int c = 1; c < count; c++) path.lineTo(points[c*2 +0], points[c*2 +1]);
		
		Paint paint = new Paint();
		paint.setStyle(Paint.Style.STROKE);
		paint.setColor(line.color);
		paint.setStrokeWidth(line.diameter*_CanvasMatrixValues[Matrix.MSCALE_X]/1.5f);
		
		canvas.drawPath(path, paint);
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent event)
	{
		try
		{
			PointPair nowHand = new PointPair(event);
			PointF mapFinger1 = convertViewToBitmapCoordinate(nowHand.Finger1);

			_HistoryHand.offer(nowHand);
			while(_HistoryHand.size() > _HistoryEdgesMax) _HistoryHand.poll();
			PointPair plainHand = new PointPair();
			for(PointPair p : _HistoryHand) plainHand.add(p);
			plainHand.scale(1.0f / (float)_HistoryHand.size());
	
			switch (event.getAction())
			{
			case MotionEvent.ACTION_DOWN:
				_Mode = DRAW;
				_ActQueue.shiftPenChange(_MainColor, _EdgeLength, _BlotCurrent, _RemainCurrent, _BaseCurrent);
				_ActQueue.shiftPoint(mapFinger1);
				_ActQueue.shiftPoint(mapFinger1);
				break;
			case MotionEvent.ACTION_POINTER_2_UP:
			case MotionEvent.ACTION_UP:
				if(_Mode == DRAW)
				{
					_ActQueue.shiftPoint(mapFinger1);
					_ActQueue.shiftEnd();
				}
				_Mode = NONE;
				break;
			case MotionEvent.ACTION_POINTER_2_DOWN:
				_Mode = MOVE;
				_MoveStartHand = new PointPair(event);
				_MoveStartMatrix.set(_CanvasMatrix);
				_HistoryHand.clear();
				break;
			case MotionEvent.ACTION_MOVE:
				if(_Mode == DRAW)
				{
					_ActQueue.shiftPoint(mapFinger1);
					doDraw();
				}
				else if(_Mode == MOVE)
				{
					PointF now = plainHand.middle();
					PointF start = _MoveStartHand.middle();
					
					_CanvasMatrix.set(_MoveStartMatrix);
					float scale = 1.0f;
					if(plainHand.length() > 30.0f)
					{
						scale = plainHand.length() / _MoveStartHand.length();
						adjustScale(_CanvasMatrix, scale, now);
					}
					_CanvasMatrix.postTranslate(now.x-start.x, now.y-start.y);
					doDraw();
				}
				break;
			}			
			return true;
		}
		catch(Exception ex)
		{
			dp(ex.getMessage());
			return false;
		}
	}
	
	private void adjustScale(Matrix m, float scale, PointF p)
	{
    	m.getValues(_CanvasMatrixValues);
    	
    	float now = _CanvasMatrixValues[Matrix.MSCALE_X];
    	float changed = now * scale;
    	if(changed < SCALE_MIN){
    		scale = SCALE_MIN / now;
    	}else if(changed > SCALE_MAX){
    		scale = SCALE_MAX / now;
    	}
    	m.postScale(scale, scale, p.x, p.y);
	}
	
	public void setBitmap(Bitmap b)
	{
		_Bitmap = b;
		_Height = _Bitmap.getHeight();
		_Width = _Bitmap.getWidth();
		_Buffer = new int[_Height*_Width];
		_Bitmap.getPixels(_Buffer, 0, _Width, 0, 0, _Width, _Height);
	}

	public void setColor(int red, int green, int blue, int alpha)
	{
    	_MainColor = Color.argb(alpha, red, green, blue);
	}
	public void setRadius(int length)
	{
    	if(length > 0) _EdgeLength = length*2 + 1;
	}

    private PointF convertViewToBitmapCoordinate(PointF p)
    {
    	_CanvasMatrix.getValues(_CanvasMatrixValues);
    	float sx = _CanvasMatrixValues[Matrix.MSCALE_X];
    	float sy = _CanvasMatrixValues[Matrix.MSCALE_Y];
    	float tx = _CanvasMatrixValues[Matrix.MTRANS_X];
    	float ty = _CanvasMatrixValues[Matrix.MTRANS_Y];
    	return new PointF((p.x - tx)/sx, (p.y - ty)/sy);
    }

	public void setBitmapZoom(float zoom)
	{		
		_CanvasMatrix.setScale(zoom, zoom);
		
		float postHeight = ((float)this.getHeight() - (float)_Height * zoom) / 2.0f;
		float postWidth = ((float)this.getWidth() - (float)_Width * zoom) / 2.0f;
		_CanvasMatrix.postTranslate(postWidth, postHeight);
		
		doDraw();
	}
	
	public void setBitmapFit()
	{		
		float scaleHeight = (float)this.getHeight() / (float)_Height;
		float scaleWidth = (float)this.getWidth() / (float)_Width;
		float scale;
		if( scaleHeight > scaleWidth ) scale = scaleWidth; else scale = scaleHeight;
		_CanvasMatrix.setScale(scale, scale);
		
		float postHeight = ((float)this.getHeight() - (float)_Height * scale) / 2.0f;
		float postWidth = ((float)this.getWidth() - (float)_Width * scale) / 2.0f;
		_CanvasMatrix.postTranslate(postWidth, postHeight);
		
		doDraw();
	}
	
	public Bitmap getBitmap(){ return _Bitmap; }

	public void pushHistoryBitmap()
	{
		try
		{
			_HistoryBitmap.push(_Bitmap);
		}
		catch(Exception ex)
		{
//			dp(ex.getMessage());
		}
	}
	public void popHistoryBitmap()
	{
		try
		{
			_ActQueue.clear();
			
			if(!_HistoryBitmap.pop()) return;			
			Bitmap bitmap = _HistoryBitmap.tail();
			if(bitmap == null)
			{
				Utility.showWarnDialog("これ以上の履歴はありません");
				return;				
			}
			bitmap.getPixels(_Buffer, 0, _Width, 0, 0, _Width, _Height);
		}
		catch(Exception ex)
		{
			dp(ex.getMessage());
		}

		doDraw();
	}
	
	/*
	 * 点のペア、線の始点・終点を保持する、あるいはピッチ用クラス
	 */
	private class PointPair
	{
		public PointF Finger1, Finger2;

		public PointPair(){ Finger1 = new PointF(); Finger2 = new PointF(); };
		public PointPair(PointF f1, PointF f2){ Finger1 = f1; Finger2 = f2; }
		public PointPair(MotionEvent e)
		{
			Finger1 = new PointF(e.getX(0), e.getY(0));
			if(e.getPointerCount() > 1){
				Finger2 = new PointF(e.getX(1), e.getY(1));
			}else{
				Finger2 = new PointF(e.getX(0), e.getY(0));;
			}
		}
		
		public void add(PointPair p)
		{
			Finger1.x += p.Finger1.x;
			Finger1.y += p.Finger1.y;
			Finger2.x += p.Finger2.x;
			Finger2.y += p.Finger2.y;
		}
		
		public void scale(float v)
		{
			Finger1.x *= v;
			Finger1.y *= v;
			Finger2.x *= v;
			Finger2.y *= v;
		}
		
		public float length()
		{
			float dx = Finger1.x - Finger2.x;
			float dy = Finger1.y - Finger2.y;
			return (float)Math.sqrt(dx*dx + dy*dy);
		}
		public PointF middle()
		{
			return new PointF((Finger1.x+Finger2.x)/2.0f, (Finger1.y+Finger2.y)/2.0f);
		}
		public PointF interpolate2(float rate1)
		{
			return new PointF(
					Finger1.x * rate1 + Finger2.x * (1.0f-rate1),
					Finger1.y * rate1 + Finger2.y * (1.0f-rate1) );
		}
		public PointF vectorNormalize()
		{
			PointF v = new PointF(Finger2.x-Finger1.x, Finger2.y-Finger1.y);
			float l = v.length();
			v.x /= l;	v.y /= l;
			return v;
		}
	}
		
	/*
	 * Bitmap を更新するタスク
	 * もし点キューがあるなら描写、ある程度仕事して眠る。そのあと更新する
	 * キューがなければ眠る。
	 * dp は使うと例外が発生
	 */
	private class DrawingBitmapTask implements Runnable
	{
		private int _TaskColor;
		private int[] _TaskEdge;	//調合された絵の具を格納
		private int _TaskDiameter = 1;	//筆の大きさ
		private SlideValue _BlotWork, _RemainWork, _BaseWork;
		private int _CellWidth = 16;
		
		public void run()	//裏で実行される
		{
			while(true)
			{
				try
				{
					Thread.sleep(1);
					_ThreadIsRefreshing = false;
					
					ActQueue.ActLine strip = _ActQueue.getHeadAct(4);
					if(strip == null) continue;
					
					ActQueue.Act head = strip.get(0);
					if(head == null) continue;
					if(head.isEnd())
					{
						doDraw();
						resetBlot();
						pushHistoryBitmap();	//履歴に追加
					}
					if(head.isPenChange())
					{
						_TaskColor = head.color;
						_TaskDiameter = head.diameter;
						_BlotWork = head.slideBlot;
						_RemainWork = head.slideRemain;
						_BaseWork = head.slideBase;
						resetEdge();						
					}
					if(head.isPoint())
					{
						LinkedList<PointF> points = interpolatePoints(strip);
						if(points != null)
						{
							for(PointF p : points)
							{
								if(_ThreadIsRefreshing) break;
								consumeEdge(p);
								drawEdge(p);
								Thread.sleep(1);
							}
						}
					}
					_ActQueue.unshift();	//頭の仕事を捨てる
				}
				catch(Exception ex)
				{
					ldp(ex.getMessage());
				}
			}
		}
		
	    private void resetEdge()	//筆先を更新
	    {
	    	int color = _TaskColor;
	    	int length = _TaskDiameter;
	    	
	    	int a = Color.alpha(color);
	    	int r = Color.red(color);
	    	int g = Color.green(color);
	    	int b = Color.blue(color);
	    	
			int edgeSize = length*length;
			int half = length / 2;
			a = (int)((double)a / Math.pow(half, 0.25));	//アルファ値の調整
			_TaskEdge = new int[edgeSize];
			for( int s = 0; s < edgeSize; s++ )
			{
				int x = s / length, y = s % length;
				int d = length - Math.abs(x-half) - Math.abs(y-half);
				
				_TaskEdge[s] = Color.argb( a * d/length, r, g, b);
			}
	    }
		
	    private void resetBlot()	//絵の具調合を初期化
	    {
	    	_BlotWork.clear();
	    	_RemainWork.clear();
	    	_BaseWork.clear();
	    }
		
	    private void consumeEdge(PointF p)	//絵の具を吸い込む
	    {
	    	int color = _TaskColor;
	    	int length = _TaskDiameter;
	    	
			int x = (int)p.x, y = (int)p.y;
			int half = (length-1)/2;

			int blot = _BlotWork.Value;
			int remain = _RemainWork.Value;
			int base = _BaseWork.Value;
			int amount = blot + remain + base;
			if( amount < 1 ) amount = 1;
			
			for( int ys = y - half; ys <= y + half; ys++ )
			{
				if( ys < 0 ) continue;
				if( ys >= _Height ) break;
				int by = ys - y + half;
				for( int xs = x - half; xs <= x + half; xs++ )
				{
					if( xs < 0 ) continue;
					if( xs >= _Width ) break;
					int bx = xs - x + half;
					int src = _Buffer[ys*_Width+xs];
					int dste = _TaskEdge[by*length+bx];
					
					int a = Color.alpha(dste);
					int r = (Color.red(dste)*remain + Color.red(src)*blot + Color.red(color)*base)/amount;
					int g = (Color.green(dste)*remain + Color.green(src)*blot + Color.green(color)*base)/amount;
					int b = (Color.blue(dste)*remain + Color.blue(src)*blot + Color.blue(color)*base)/amount;
					
					_TaskEdge[by*length+bx] = Color.argb(a, r, g, b);
				}
			}

			_BlotWork.grow();
			_RemainWork.grow();
			_BaseWork.grow();
			Log.d("My", "v=" + _BaseWork.Value);
	    }
		
		private void drawEdge(PointF p)	//1点描く
		{
	    	int length = _TaskDiameter;
			
			int x = (int)p.x, y = (int)p.y;
			int half = length/2;
			try
			{
				for( int ys = y - half; ys <= y + half; ys++ )
				{
					if( ys < 0 ) continue;
					if( ys >= _Height ) break;
					int ey = ys - y + half;
					for( int xs = x - half; xs <= x + half; xs++ )
					{
						if( xs < 0 ) continue;
						if( xs >= _Width ) break;
						int ex = xs - x + half;
						
						int dst = _Buffer[ys*_Width+xs];
						int dstr = Color.red(dst);
						int dstg = Color.green(dst);
						int dstb = Color.blue(dst);

						int src1 = _TaskEdge[ey*length+ex];
						int src1a = Color.alpha(src1);
						int src1r = Color.red(src1);
						int src1g = Color.green(src1);
						int src1b = Color.blue(src1);

						src1a = (int)( getSurfaceIntensity(xs, ys) * (float)src1a );
						int dsta = 256 - src1a;
						
						int r = (src1r*src1a + dstr*dsta)/256;
						int g = (src1g*src1a + dstg*dsta)/256;
						int b = (src1b*src1a + dstb*dsta)/256;
						_Buffer[ys*_Width+xs] = Color.argb(255, r, g, b);
					}
				}
			}
			catch(Exception ex)
			{
				ldp(ex.getMessage());
				Log.d("My", ex.getMessage() + " " + x + "," + y);
			}
		}

		private LinkedList<PointF> interpolatePoints(ActQueue.ActLine strip)
		{
			LinkedList<PointF> list = new LinkedList<PointF>();
			int size = strip.acts.size();
			if(size < 4) return null;
			if(size > 4) size = 4;
			
			PointF[] p = new PointF[4];
			for(int s = 0; s < size; s++)
			{
				ActQueue.Act a = strip.acts.get(s);
				if(!a.isPoint()) return null;
				p[s] = a.point;
			}

			PointPair pair = new PointPair(p[1], p[2]);
			float length = pair.length();

			PointPair t1 = new PointPair(p[0], p[2]);
			PointPair t2 = new PointPair(p[1], p[3]);
			PointF n1 = t1.vectorNormalize();
			PointF n2 = t2.vectorNormalize();
			float rate = length / 3.0f;
			PointF s1 = new PointF(p[1].x+n1.x*rate, p[1].y+n1.y*rate);
			PointF s2 = new PointF(p[2].x-n2.x*rate, p[2].y-n2.y*rate);
			
			int nmax = (int)(length / 2.0f) + 1;
			for(int n = 0; n < nmax; n++)
			{
				float r = (float)n / (float)nmax;
				float ir = 1.0f - r;
				float x = p[1].x *ir*ir*ir + s1.x * 3.0f*ir*ir*r + s2.x * 3.0f*ir*r*r + p[2].x *r*r*r;
				float y = p[1].y *ir*ir*ir + s1.y * 3.0f*ir*ir*r + s2.y * 3.0f*ir*r*r + p[2].y *r*r*r;
				list.add(new PointF(x, y));
			}
			return list;
		}
		
		private void ldp(String s)	//デバッグ用
		{
			final String dpstring = s;
			_Handler.post(
				new Runnable()
				{
					public void run(){ dp(dpstring); }
				}
			);
		}
		
		private float getSurfaceIntensity(int x, int y)
		{
			if(_Surface == SURFACE_DITHER){
				float dx = FloatMath.sin((float)Math.PI * 2.0f * x / _CellWidth);
				float dy = FloatMath.sin((float)Math.PI * 2.0f * y / _CellWidth);
				float dxx = dx * dx;
				float dyy = dy * dy;
				float d = dxx > dyy ? dxx*dxx : dyy*dyy;	//0-1
				return d * 0.4f + 0.6f;
			}else if(_Surface == SURFACE_RANDOM){
				float dx = FloatMath.sin((float)Math.PI*x*23.0f / _CellWidth )
						* FloatMath.sin((float)Math.PI*x*37.0f / _CellWidth );
				float dy = FloatMath.sin((float)Math.PI*y*53.0f / _CellWidth )
						* FloatMath.sin((float)Math.PI*y*17.0f / _CellWidth );
				float dxy = FloatMath.sin((float)Math.PI*(x+y)*19.0f / _CellWidth );
				float d = (dx*dx + dy*dy + dxy*dxy)/3.0f;
				return d * 0.4f + 0.6f;
			}else{
				return 1.0f;
			}
		}
	}
	
	/*
	 * デバグ用
	 */
	public PastelTouchActivity _Activity;
	public void setActivity(PastelTouchActivity a){ _Activity = a; }
	private void dp(String s)
	{
		if(s == null) return;
		//showWarnDialog(s);
		if(_Activity != null) _Activity.dp(s);
		Log.d("My",s);
	}
}
