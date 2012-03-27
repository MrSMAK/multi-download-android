package com.multidownload;

import java.io.File;
import java.util.Vector;
import android.content.Context;
import android.content.SharedPreferences;

public class Download {
	public String url;
	/**
	 * Name of file
	 */
	public String name;
	/**
	 * extension of file
	 */
	public String type;
	/**
	 * Mime type of file
	 */
	public String mime;
	/**
	 * Size of file in bytes
	 */
	public long bytes;
	/**
	 * Downloaded size
	 */
	public long downloaded;
	/**
	 * Percent complete (0->100)
	 */
	public int perc;
	/**
	 * Path to save file
	 */
	public String filepath;
	/**
	 * Indentity of download, >0
	 */
	public int idDownload;
	/**
	 * State of thread
	 * STATUS_FAILED = 4;
	 * STATUS_PAUSED = 2;
	 * STATUS_PENDING = 0;
	 * STATUS_RUNNING = 1;
	 * STATUS_SUCCESSFUL = 3;
	 * STATUS_MERGING = 5;
	 */
	public int state;
	DownloadThread thread;
	String lastMod;
	/**
	 * Maximum parts to split, 1 <=max<= 32
	 */
	public static int maxNumbersOfParts;
	public Vector<DownloadPart>part;
	OnCompleteListener onCompleteListenner;
	
	public Download(Context c,String savePath){
		downloaded = perc = 0;idDownload = 0;
		part = new Vector<DownloadPart>();
		SharedPreferences sp = c.getSharedPreferences("iddownload", Context.MODE_PRIVATE);
		idDownload = sp.getInt("iddownload", 0)+1;
		sp.edit().putInt("iddownload", idDownload);
		filepath = savePath;
		thread = new DownloadThread(c, this);
	}
	
	public void download(){
		thread.start();
	}
	
	public void setOnCompleteListenner(OnCompleteListener l){
		onCompleteListenner = l;
	}
	
	public void setType(){
		if(mime.contains("flv"))type = "flv";
        if(mime.contains("avi"))type = "avi";
        if(mime.contains("matroska"))type = "mkv";
        if(mime.contains("mpeg3"))type = "mp3";
        if(mime.contains("mpeg"))type = "mp3";
        if(mime.contains("mp4"))type = "mp4";
        if(mime.contains("wav"))type = "wav";
        if(mime.contains("wma"))type = "wma";
        if(mime.contains("wmv"))type = "wmv";
        if(mime.contains("rar"))type = "rar";
        if(mime.contains("zip"))type = "zip";
        if(mime.contains("msword"))type = "doc";
        if(mime.contains("xml"))type = "xml";
        if(mime.contains("pdf"))type = "pdf";
        if(mime.contains("mobipocket"))type = "prc";
        if(mime.contains("mspowerpoint"))type = "ppt";
        if(mime.contains("png"))type = "png";
        if(mime.contains("jpg"))type = "jpg";
        if(mime.contains("jpeg"))type = "jpg";
        if(mime.contains("flac"))type = "flac";
        if(mime.contains("plain"))type = "txt";
        if(mime.contains("android.package"))type = "apk";
	}
	public boolean isVideo(){
		if(mime.contains("video"))return true;
		return false;
	}
	public boolean isMusic(){
		if(mime.contains("audio"))return true;
		return false;
	}
	public boolean isPic(){
		if(mime.contains("image"))return true;
		return false;
	}

	public static final int STATUS_FAILED = 4;
	public static final int STATUS_PAUSED = 2;
	public static final int STATUS_PENDING = 0;
	public static final int STATUS_RUNNING = 1;
	public static final int STATUS_SUCCESSFUL = 3;
	public static final int STATUS_MERGING = 5;
	
	
	protected DownloadPart makePart(int i,boolean last){
		long start,end,bytesPerPart;
		bytesPerPart = bytes / 16;
		start = i * bytesPerPart;
		end = start + bytesPerPart;
		if(last)end = bytes;
		DownloadPart p = new DownloadPart(this, i, start, end);
		return p;
	}
	
	protected void deletePartWhenComplete(){
		File f;
		for(int i=0;i<part.size();i++){
			try{
				f = new File(part.get(i).filepath);
				f.delete();
			}catch (Exception e) {
			}
		}
		onCompleteListenner.OnComplete(this);
	}
}
