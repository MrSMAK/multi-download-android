package com.multidownload;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.Vector;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.params.CookiePolicy;
import org.apache.http.impl.client.DefaultHttpClient;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.StatFs;
import android.util.Log;

public class DownloadThread extends Thread {
	public static int MAXPART = 16;
	public String error = "";
	String sFileTemp = "";
	boolean bisinterrupted = false;
	boolean done = false;
	boolean pause;
	
	InputStream 		binaryreader = null;
	HttpGet				httpget = null,get1;
	HttpClient			httpclient = null,client1;
    HttpResponse		response = null,res1;
    NotificationManager nm;
    Notification not;
    Download dl;
    Handler h;
    long lastnotify = 0;
    int lastPerc = -1;
    float speed = 0;
    Vector<DownloadChildThread> childThread;

	public DownloadThread(Context c,Download dl) {
		super();
		this.dl = dl;
		dl.state = Download.STATUS_PENDING;
		h = new Handler();
	}

	
	private String getFileName(URI url) {
		try{
	        String fileName = url.getPath();
	        fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
	        if(fileName.lastIndexOf(".")>fileName.length()-5){
	        	fileName = fileName.substring(0,fileName.lastIndexOf("."));
	        }
	        return fileName;
		}catch (Exception e) {
			return "file";
		}
    }
	
	String size(long b)
    {
    	if(b<=0)return "0 MB";
    	try{
	    	String s = ""+((float)b/(1024f*1024f))+"00";
	    	int i = s.indexOf(".");
	    	s = s.substring(0,i+2);
	    	return s+"MB";
    	}catch (Exception e) {
    		return "0 MB";
		}
    }
	@Override
	public void interrupt() {
		super.interrupt();
	}
	
	Uri raw;URI fine;
	boolean downloadone() {
		boolean rc = false;
		try {
			if (dl.url.equals("")) return(false);
		} catch (Exception npe) {
			return(false);
		}
		try {
			pause = false;
			raw = Uri.parse(dl.url);
			fine = new URI(raw.getScheme(),raw.getUserInfo(),raw.getHost(),
					raw.getPort(),raw.getPath(),raw.getQuery(),raw.getFragment());
			if(dl.name==null||dl.name.length()<=0){
				dl.name = getFileName(fine);
				dl.type = getFileExtension(fine);
			}
			httpclient = new DefaultHttpClient();
			httpclient.getParams().setParameter(ClientPNames.COOKIE_POLICY, CookiePolicy.BEST_MATCH);
			httpget = new HttpGet(fine);
			httpget.addHeader("user-agent", "Mozilla/5.0 (Linux; U; Android 2.3.3) Gecko/20100101 Firefox/8.0");
			httpget.addHeader("accept-language","en-us,en;q=0.5");
			if(dl.lastMod!=null)httpget.addHeader("If-Range", "" + dl.lastMod + "-");
		} catch (Exception e) {
			error = "Url invalid.";
			dl.state = Download.STATUS_FAILED;
			e.printStackTrace();
		}
		try {
			response = httpclient.execute(this.httpget);
            if (response.getStatusLine().getStatusCode() / 100 != 2) {
                error = "Server error:"+response.getStatusLine().getStatusCode() +response.getStatusLine().getReasonPhrase();
                dl.state = Download.STATUS_FAILED;
                return false;
            }
		} catch (Exception cpe) {
			error = "Url invalid.";
			dl.state = Download.STATUS_FAILED;
			cpe.printStackTrace();
		}
		HttpEntity entity = null;
        entity = response.getEntity();
        
        if (entity != null) {
            try {
            	dl.mime = response.getFirstHeader("Content-Type").getValue().toLowerCase();
            	try{
            		if(dl.bytes<=0)dl.bytes = Long.parseLong(response.getFirstHeader("Content-Length").getValue());
            		Log.e("CONTENT-LENGTH", ""+response.getFirstHeader("Content-Length").getValue());
            		if(dl.lastMod==null&&response.getFirstHeader("Last-Modified")!=null)
            			dl.lastMod = response.getFirstHeader("Last-Modified").toString();
            		if(dl.lastMod==null&&response.getFirstHeader("ETag")!=null)
            			dl.lastMod = response.getFirstHeader("ETag").toString();
            	}catch (Exception e) {
				}
            	httpclient.getConnectionManager().shutdown();
            	dl.setType();
        		MAXPART = Download.maxNumbersOfParts;
            	if(dl.bytes<1024)MAXPART = 1;
        		splitPart(MAXPART);
        		manageDownload();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
		return(rc);
	}
	private String getFileExtension(URI url) {
		try{
	        String fileName = url.getPath();
	        int i = fileName.lastIndexOf(".");
	        return fileName.substring(i+1);
		}catch (Exception e) {
			return "txt";
		}
    }
	void splitPart(int part){
		MAXPART = Download.maxNumbersOfParts;
		if(MAXPART>32)MAXPART = 32;
		if(MAXPART<1)MAXPART = 1;
		childThread = new Vector<DownloadChildThread>();
		DownloadPart p;
		DownloadChildThread dct;
		if(dl.part.size()>0)
			for(int i=0;i<dl.part.size();i++){
				p = dl.part.get(i);
				dct = new DownloadChildThread(p);
				childThread.add(dct);
				dct.start();
			}
		else
			for(int i=0;i<MAXPART;i++){
				if(i==MAXPART-1)p = dl.makePart(i,true);
				else p = dl.makePart(i,false);
				dl.part.add(p);
				dct = new DownloadChildThread(p);
				childThread.add(dct);
				dct.start();
			}
		dl.state = Download.STATUS_RUNNING;
//		UpadteNotify();
	}
	
	boolean complete(){
		for(int i=0;i<childThread.size();i++)
			if(!childThread.get(i).done)return false;
		return true;
	}
	String error(){
		for(int i=0;i<childThread.size();i++)
			if(childThread.get(i).error!=null)return childThread.get(i).error;
		return null;
	}
	long downloaded(){
		long tot=0;
		for(int i=0;i<childThread.size();i++)
			tot+=childThread.get(i).dl.downloaded;
		return tot;
	}
	
	boolean merge(){
		File f = new File(dl.filepath);
		f.getParentFile().mkdirs();
		int done=1;
		byte buffer[] = new byte[1024];
		try{
			f.createNewFile();
			FileOutputStream fos = new FileOutputStream(f);
			
			for(int i=0;i<dl.part.size();i++){
				FileInputStream fis;
				fis = new FileInputStream(dl.part.get(i).filepath);
				done = 1;
				while(done>0){
					done = fis.read(buffer);
					if(done>0)fos.write(buffer,0,done);
				}
				fis.close();
				fos.flush();
			}
			fos.close();
		}catch (Exception e) {
			dl.state = Download.STATUS_FAILED;
			e.printStackTrace();
			error = "Not enough memory space";
			return false;
		}
		return true;
	}
	
	void manageDownload(){
		StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
		long bytesAvailable = (long)stat.getBlockSize() *(long)stat.getBlockCount();
		if(dl.bytes>=bytesAvailable){
			error = "Not enough space.";
			dl.state = Download.STATUS_FAILED;
			pause();
			return;
		}
		dl.state = Download.STATUS_RUNNING;
		while(!pause&&!done){
			if(complete())done = true;
			dl.downloaded = downloaded();
			dl.perc = (int) (dl.downloaded*100/dl.bytes);
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		if(dl.downloaded>=dl.bytes){
			dl.state = Download.STATUS_MERGING;
			if(merge()){
				dl.state = Download.STATUS_SUCCESSFUL;
				dl.deletePartWhenComplete();
			}
		}
		else if(pause){
			dl.state = Download.STATUS_PAUSED;
		}
		else{
			dl.state = Download.STATUS_FAILED;
		}
	}
	
	long iPercentage = (long) -1;
	String musicfolder;
	String videofolder;
	
	long lastUpSp=0;
	long timesp;
	long tempDl=0,lastDl=0;
	
    String sizeK(float b)
    {
    	if(b<=0)return "0 KB/s";
    	try{
	    	String s = ""+b+"00";
	    	int i = s.indexOf(".");
	    	s = s.substring(0,i+2);
	    	return s+" KB/s";
    	}catch (Exception e) {
    		return "0 KB/s";
		}
    }

	public void run() {
		try {
			downloadone();
			nm.cancel((int)getId());
		} catch (Exception e) {
		}
	}
	
	public void pause(){
		pause = true;
		closePart();
	}
	
	void closePart(){
		for(int i=0;i<childThread.size();i++){
			childThread.get(i).pause();
		}
	}
	
	@Override
	protected void finalize() throws Throwable {
		super.finalize();
	}
}
