package com.multidownload;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.params.CookiePolicy;
import org.apache.http.impl.client.DefaultHttpClient;
import android.net.Uri;
import android.util.Log;

public class DownloadChildThread extends Thread {
	public String error = "";
	String sFileTemp = "";
	boolean done = false;
	boolean pause;
	
	InputStream 		binaryreader = null;
	HttpGet				httpget = null;
	HttpClient			httpclient = null;
    HttpResponse		response = null;
    DownloadPart dl;

	public DownloadChildThread(DownloadPart dl) {
		super();
		this.dl = dl;
	}
	
	boolean downloadone() {
		boolean rc = false;
		try {
			Log.e("part "+dl.idPart, ""+dl.start+"->"+dl.end+" ("+dl.downloaded+"/"+dl.bytes+")");
			pause = false;
			Uri raw = Uri.parse(dl.dl.url);
			URI fine = new URI(raw.getScheme(),raw.getUserInfo(),raw.getHost(),
					raw.getPort(),raw.getPath(),raw.getQuery(),raw.getFragment());
			httpclient = new DefaultHttpClient();
			httpclient.getParams().setParameter(ClientPNames.COOKIE_POLICY, CookiePolicy.BEST_MATCH);
			httpget = new HttpGet(fine);
			httpget.addHeader("user-agent", "Mozilla/5.0 (Linux; U; Android 2.3.3) Gecko/20100101 Firefox/8.0");
			httpget.addHeader("accept-language","en-us,en;q=0.5");
			httpget.addHeader("Range", "bytes=" + (dl.start+dl.downloaded) + "-");
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
                Log.e("ERR", ""+error);
                return false;
            }
            if(dl.lastMod==null&&response.getFirstHeader("Last-Modified")!=null)
    			dl.lastMod = response.getFirstHeader("Last-Modified").toString();
    		if(dl.lastMod==null&&response.getFirstHeader("ETag")!=null)
    			dl.lastMod = response.getFirstHeader("ETag").toString();
		} catch (Exception cpe) {
			error = "Url invalid.";
			dl.state = Download.STATUS_FAILED;
			cpe.printStackTrace();
		}
		HttpEntity entity = null;
        entity = response.getEntity();
        if (entity != null) {
            try {
            	binaryreader = new BufferedInputStream( entity.getContent());
    			savebinarydata();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
		return(rc);
	}

	long iPercentage = (long) -1;
	String musicfolder;
	String videofolder;
	long remain;
	
	void savebinarydata() throws IOException {
		FileOutputStream fos = null;
		try {
			error = "";
			File f = new File(dl.filepath);
			if(dl.downloaded<=0||!f.canRead()){
				f.getParentFile().mkdirs();
				f.createNewFile();
				dl.downloaded = 0;
			}
			Long iBytesReadSum = (long) dl.downloaded;
			if(dl.bytes<=0)dl.bytes = binaryreader.available();
			if(dl.downloaded>0)fos = new FileOutputStream(f,true);
			else fos = new FileOutputStream(f);
			byte[] bytes = new byte[4096];
			Integer iBytesRead = 1;
			while (iBytesRead>0) {
				try{
					remain = dl.end - dl.downloaded - dl.start;
					if(remain>4096)remain=4096;
					iBytesRead = this.binaryreader.read(bytes,0,(int)remain);
					if(iBytesRead==-1||pause)break;
					dl.state = Download.STATUS_RUNNING;
					iBytesReadSum += iBytesRead;
					dl.downloaded = iBytesReadSum;
					try {
						fos.write(bytes,0,iBytesRead);
					} catch (Exception ioob) {
						error = "Not enough space.";
						break;
					}
				}catch (Exception e) {
					dl.state = Download.STATUS_PAUSED;
					break;
				}
			}
			if(dl.downloaded>=dl.bytes)
			{
				done = true;
				dl.state = Download.STATUS_SUCCESSFUL;
			}
			else if(pause)dl.state = Download.STATUS_PAUSED;
			else dl.state = Download.STATUS_FAILED;
		} catch (FileNotFoundException fnfe) {
			done = false;
			error = "Cant create file.";
			throw(fnfe);
		} catch (IOException ioe) {
			error = "Cant create file.";
			done = false;
			throw(ioe);
		}catch (SecurityException ioe) {
			error = "Cant create file. Access denied.";
			done = false;
			throw(ioe);
		} finally {
			try {
				fos.close();
				this.binaryreader.close();
				this.httpclient.getConnectionManager().shutdown();
			} catch (Exception e) {
			}
		} // try
	} // savebinarydata()
	
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
		} catch (Exception e) {
		}
	}
	
	public void pause(){
		pause = true;
	}
}
