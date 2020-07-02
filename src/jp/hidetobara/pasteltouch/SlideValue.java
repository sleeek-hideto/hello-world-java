package jp.hidetobara.pasteltouch;

/*
 * 汚れなどの濃度指定クラス
 */
public class SlideValue
{
	private int Min = 0, Max = 256;
	private int Gain = 4;
	public int Value = 0;
	
	public SlideValue(int min, int max, int gain){ Min = min; Max = max; Gain = gain; Value = min; }
	public void clear(){ Value = Min; }
	public void grow(){ Value += (Max - Value) / Gain; }
	public SlideValue clone(){ return new SlideValue(Min, Max, Gain); }
}
