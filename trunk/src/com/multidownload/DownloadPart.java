package com.multidownload;


public class DownloadPart {
	static String TempFolder = "/sdcard/.Temp";
	public long bytes;
	public long downloaded;
	public String filepath;
	public DownloadChildThread thread;
	public int idPart;
	public long start,end;
	public Download dl;
	public int state;
	public String lastMod;
	
	public DownloadPart(Download dl,int idPart,long start,long end){
		this.dl = dl;
		this.idPart = idPart;
		this.start = start;this.end = end;
		bytes = end - start;
		downloaded = 0;
		this.state = Download.STATUS_PENDING;
		filepath = TempFolder+"/"+dl.idDownload+"part"+idPart;
	}
	public static void setTempFolder(String s){
		TempFolder = s;
	}
}
