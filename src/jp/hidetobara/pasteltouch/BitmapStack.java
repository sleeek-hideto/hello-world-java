package jp.hidetobara.pasteltouch;

import java.io.File;

import android.graphics.Bitmap;

/*
 * スタックをファイルを使って実現する
 */
public class BitmapStack
{
	private Object _Lock = new Object();
	private int _Capacity;
	private Item[] _Items;
	private int _LastIndex = 0;
	
	public BitmapStack(int capacity)
	{
		_Capacity = capacity;
		_Items = new Item[_Capacity];
		
		for(int i = 0; i < _Capacity; i++)
		{
			_Items[i] = new Item();
			_Items[i].filename = "tmp" + i;
		}
	}
	
	public void clear()
	{
		synchronized (_Lock)
		{
			for (Item i : _Items){ i.enable = false; }
			_LastIndex = 0;
		}
	}
	
	public void push(Bitmap b)
	{
		synchronized (_Lock)
		{
			_LastIndex = (++_LastIndex)%_Capacity;
			Item item = _Items[_LastIndex];
			item.file = Utility.saveBitmapPng(item.filename, b);
			item.enable = true;
		}
	}
	
	public boolean pop()
	{
		synchronized (_Lock)
		{
			Item item = _Items[_LastIndex];
			if(!item.enable) return false;
			
			item.enable = false;
			
			--_LastIndex;
			if(_LastIndex < 0) _LastIndex = _Capacity - 1;
			return true;
		}
	}
	
	public Bitmap tail()
	{
		synchronized (_Lock)
		{
			Item item = _Items[_LastIndex];
			if(!item.enable) return null;

			return Utility.loadBitmap(item.file);
		}
	}
	
	private class Item
	{
		public String filename;
		public File file;
		public boolean enable = false;
	}
}
