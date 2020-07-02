package jp.hidetobara.pasteltouch;

import java.util.LinkedList;

import android.graphics.PointF;

/*
 * ユーザの行動列と
 * その行動をとる前の状態を格納
 */
public class ActQueue
{
	private LinkedList<Act> _Queue = new LinkedList<Act>();	//行動を記録
	private int _Color;
	private int _Diameter;
	
	public int getHeadColor(){ return _Color; }
	public int getHeadDiameter(){ return _Diameter; }
	
	public void shiftEnd()
	{
		Act a = new Act();
		a.type = ACT_END;
		shift(a);
	}
	
	public void shiftPenChange(int color, int diameter, SlideValue blot, SlideValue remain, SlideValue base)
	{
		Act a = new Act();
		a.type = ACT_PEN_CHANGE;
		a.color = color;
		a.diameter = diameter;
		a.slideBlot = blot.clone();
		a.slideRemain = remain.clone();
		a.slideBase = base.clone();
		shift(a);
	}
	
	public void shiftPoint(PointF p)
	{
		Act a = new Act();
		a.type = ACT_POINT;
		a.point = p;
		shift(a);
	}
	
	private void shift(Act a)
	{
		synchronized (_Queue)
		{
			_Queue.add(a);
		}
	}
	
	public void clear()
	{
		synchronized (_Queue)
		{
			_Queue.clear();
		}
	}
	
	/*
	 * 大雑把な複数の線を返す
	 */
	public LinkedList<ActLine> getWholeLines()
	{
		LinkedList<ActLine> lines = new LinkedList<ActLine>();
		ActLine line = null;
		int color = _Color;
		int diameter = _Diameter;
		synchronized (_Queue)
		{
			if(_Queue.size() == 0) return null;
			
			for(Act a : _Queue)
			{
				if(a.type == ACT_PEN_CHANGE)
				{
					color = a.color;
					diameter = a.diameter;
				}
				if(line == null)
				{
					line = new ActLine();
					line.color = color;
					line.diameter = diameter;
				}
				
				if(a.type == ACT_PEN_CHANGE) line.add(a);
				if(a.type == ACT_POINT) line.add(a);
				if(a.type == ACT_END){
					lines.add(line);
					line = null;
				}
			}
		}
		if(line != null) lines.add(line);
		return lines;
	}

	/*
	 * 線のトップの詳細な切れ端を返す
	 */
	public ActLine getHeadAct(int neighbors)
	{
		ActLine line = new ActLine();
		line.color = _Color;
		line.diameter = _Diameter;
		synchronized (_Queue)
		{
			int size = _Queue.size();
			for(int n = 0; n < size; n++)
			{				
				Act a = _Queue.get(n);
				line.add(a);
				
				if(a.type == ACT_END) return line;	//終点にきたとき
				if(a.type == ACT_PEN_CHANGE) return line;	//始点にきたとき
				if(line.count() >= neighbors) return line;	//必要な隣接点が集まったとき
			}
		}
		return null;
	}
	
	/*
	 * 線のトップを捨てる
	 */
	public Act unshift()
	{
		synchronized (_Queue)
		{
			if(_Queue.size() == 0) return null;
			
			Act a = _Queue.poll();
			if(a.type == ACT_PEN_CHANGE)
			{
				_Color = a.color;
				_Diameter = a.diameter;
			}
			return a;
		}
	}
	
	public final int ACT_END = 0;
	public final int ACT_PEN_CHANGE = 1;
	public final int ACT_POINT = 2;
	public class Act
	{
		public int type;
		public int color;
		public int diameter;
		public PointF point;	//自身の点
		public SlideValue slideBlot, slideRemain, slideBase;
		
		boolean isEnd(){ return this.type == ACT_END; }
		boolean isPenChange(){ return this.type == ACT_PEN_CHANGE; }
		boolean isPoint(){ return this.type == ACT_POINT; }
	}
	
	public class ActLine
	{
		public LinkedList<Act> acts = new LinkedList<Act>();
		public int color;
		public int diameter;
		
		public int count(){ return acts.size(); }
		public void add(Act a){ acts.add(a); }
		
		public Act get(int n)
		{
			if(acts.size() <= n) return null;
			return acts.get(n);
		}
		
		public float[] getFloatArray()
		{
			int size = 0;
			for(Act a : acts)
			{
				if(a.isPoint()){ size++; }
			}
			if(size == 0) return null;
			
			float[] array = new float[size*2];
			int i = 0;
			for(Act a : acts)
			{
				if( !a.isPoint() ) continue;
				
				array[i*2   ] = a.point.x;
				array[i*2 +1] = a.point.y;
				i++;
			}
			return array;
		}
		
		public PointF getPointF(int n)
		{
			if(acts.size() <= n) return null;
			Act a = acts.get(n);
			if(a.type != ACT_POINT) return null;
			return a.point;
		}
	}
}
