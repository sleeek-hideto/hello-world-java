package jp.hidetobara.pasteltouch;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

public class Utility
{
	private static final String TAG = "Util";	
	
	public static Context context = null;
	
	public static File saveBitmapJpeg(String filehead, Bitmap source)
	{
		return saveBitmap(filehead + ".jpg", source, CompressFormat.JPEG);
	}
	public static File saveBitmapPng(String filehead, Bitmap source)
	{
		return saveBitmap(filehead + ".png", source, CompressFormat.PNG);
	}
	public static File saveBitmap(String filename, Bitmap source, CompressFormat format)
	{
		String directory = Environment.getExternalStorageDirectory().toString() + "/" + PastelTouchActivity.APPLICATION_NAME;
		
		OutputStream outputStream = null;
		File file = null;
		try
		{
		    File dir = new File(directory);
		    if (!dir.exists()) {
		        dir.mkdirs();
		    }
		    file = new File(directory, filename);
		    if (file.canWrite() || file.createNewFile()) {
		        outputStream = new FileOutputStream(file);
		        source.compress(format, 100, outputStream);
		    }
		} catch (FileNotFoundException ex) {
		    Log.w(TAG, ex);
		    return null;  
		} catch (IOException ex) {  
		    Log.w(TAG, ex);
		    return null;  
		} finally {  
			if (outputStream != null) {  
			    try {
			        outputStream.close();
			    } catch (Throwable t) {  }
			}
		}
		return file;
	}

    public static Bitmap loadBitmap(File file)
    {
    	try
    	{
    		String path = file.getPath();
			return BitmapFactory.decodeFile(path);
    	}
		catch(Exception ex)
		{
			Log.w(TAG, ex.getMessage());
			return null;
		}		
    }
	
    /*
     * 変更可能なbitmapを読み込む
     */
    public static Bitmap loadBitmap(Uri uri)
    {
    	try
    	{
			return MediaStore.Images.Media
					.getBitmap(context.getContentResolver(), uri)
					.copy(Config.ARGB_8888, true);
    	}
		catch(Exception ex)
		{
			Log.w(TAG, ex.getMessage());
			return null;
		}		
    }
    
    public static boolean deletePng(String filehead)
    {
    	try
    	{
    		String directory = Environment.getExternalStorageDirectory().toString() + "/" + PastelTouchActivity.APPLICATION_NAME;
    		File file = new File(directory, filehead + ".png");
    		if(file.exists()) file.delete();
    	}
    	catch(Exception ex)
    	{
    		return false;
    	}
    	return true;
    }
    
    public static String getRootPath()
    {
    	return Environment.getExternalStorageDirectory().toString() + "/" + PastelTouchActivity.APPLICATION_NAME;
    }
    
    public static String getSuffix(File file)
    {
		String suffix = "";
		if (!file.isDirectory())
		{
			String filename = file.getName();
			
			int lastdotposition = filename.lastIndexOf(".");
			if (lastdotposition != -1) {
				suffix = filename.substring(lastdotposition + 1);
			}
		}    
		return suffix;
    }
    
	public static void showWarnDialog(String s)
	{
		new AlertDialog.Builder(context).setTitle("注意").setMessage(s).setPositiveButton("OK", null).show();
	}
}
