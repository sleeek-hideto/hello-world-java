package jp.hidetobara.pasteltouch;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

public class LocalStorage
{
	final String STORAGE_NAME = "pastel_touch";
	
	final String PEN_TYPE_NAME = "pen_type";
	final String PEN_SIZE_NAME = "pen_size";
	final String PEN_COLOR_NAME = "pen_color";
	final String CANVAS_SURFACE_NAME = "canvas_surface";
	
	private static LocalStorage _Instance = null;
	public static LocalStorage Factory(Activity a)
	{
		if(_Instance == null) _Instance = new LocalStorage(a);
		return _Instance;
	}
	
	private SharedPreferences _Preferences;
	
	private int _PenSize = 4;
	private int _PenType = 0;
	private int _PenColor = 0xff000000;
	private String _CanvasSurface = "random";

	public int getPenSize(){ return _PenSize; }
	public int getPenColor(){ return _PenColor; }
	public int getPenType(){ return _PenType; }
	public String getCanvasSurface(){ return _CanvasSurface; }
	public void setPenSize(int s){ _PenSize = s; }
	public void setPenColor(int c){ _PenColor = c; }
	public void setPenType(int s){ _PenType = s; }
	public void setCanvasSurface(String s){ _CanvasSurface = s; }
	
	private LocalStorage(Activity a)
	{
		_Preferences = a.getSharedPreferences(STORAGE_NAME, Activity.MODE_WORLD_READABLE|Activity.MODE_WORLD_WRITEABLE);
		load();
	}
	
	public void load()
	{
		_PenSize = _Preferences.getInt(PEN_SIZE_NAME, 4);
		_PenColor = _Preferences.getInt(PEN_COLOR_NAME, 0xff000000);
		_PenType = _Preferences.getInt(PEN_TYPE_NAME, 0);
		_CanvasSurface = _Preferences.getString(CANVAS_SURFACE_NAME, "");
	}
	
	public void save()
	{
		Editor editor = _Preferences.edit();
		editor.putInt(PEN_SIZE_NAME, _PenSize);
		editor.putInt(PEN_COLOR_NAME, _PenColor);
		editor.putInt(PEN_TYPE_NAME, _PenType);
		editor.putString(CANVAS_SURFACE_NAME, _CanvasSurface);		
		editor.commit();
	}
}
