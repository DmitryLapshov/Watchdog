package watchdog;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 *
 * @author kit
 */
public class Request {
    
    public static enum Operations {
        READING, WRITING
    }
    
    public static int timeout;
    public static String useragent;
    public long timespan;
    public String name;
    public Operations operation;
    public int respCode;
    public String respMessage = "";
    public String respException = "";
    public String respFile = "";

    public Request(String name) {
        this.name = name;
    }

    public String get(String type) {
        URL url;
        HttpURLConnection con;
        String line;
        InputStream _is;
        operation = Operations.READING;
        StringBuilder buff = new StringBuilder();
        long started = System.currentTimeMillis();
        try {
            url = new URL(name);
            con = (HttpURLConnection)url.openConnection();
            con.setConnectTimeout(timeout);
            con.setReadTimeout(timeout);
            con.setRequestMethod("GET");
            con.setRequestProperty("Connection", "close");
            con.setRequestProperty("Content-Type", type);
            con.setRequestProperty("Content-Language", "en-US");
            con.addRequestProperty("User-Agent", useragent);
            //con.setUseCaches(true);
            respCode = con.getResponseCode();
            respMessage = con.getResponseMessage();
            if(respCode < 400) {
                _is = con.getInputStream();
            }
            else {
            /* error from server */
                _is = con.getErrorStream();
            }
            BufferedReader br = new BufferedReader(new InputStreamReader(_is));
            while ((line = br.readLine()) != null){
                buff.append(line).append("\n");
            }
            br.close();
            _is.close();
            con.disconnect();
        }
        catch(IOException e) {
            respCode = 0;
            respException = e.toString();
            System.out.println(name + " " + respException);
        }
        finally {
            timespan = System.currentTimeMillis() - started;
            return buff.toString();
        }
    }

    public String post(String post, String type) {
        URL url;
        HttpURLConnection con;
        String line;
        InputStream _is;
        operation = Operations.WRITING;
        StringBuilder buff = new StringBuilder();
        long started = System.currentTimeMillis();
        try {
            url = new URL(name);
            con = (HttpURLConnection)url.openConnection();
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.setConnectTimeout(timeout);
            con.setReadTimeout(timeout);
            con.setRequestProperty("Connection", "close");
            con.setRequestProperty("Content-Type", type);
            con.setRequestProperty("Content-Length", Integer.toString(post.getBytes().length));
            con.setRequestProperty("Content-Language", "en-US");
            con.addRequestProperty("User-Agent", useragent);
            //con.setUseCaches(true);
            DataOutputStream dos = new DataOutputStream(con.getOutputStream());
            dos.writeBytes(post);
            dos.flush();
            dos.close();
            respCode = con.getResponseCode();
            respMessage = con.getResponseMessage();
            if(respCode < 400) {
                _is = con.getInputStream();
            }
            else {
            /* error from server */
                _is = con.getErrorStream();
            }
            BufferedReader br = new BufferedReader(new InputStreamReader(_is));
            while ((line = br.readLine()) != null){
                buff.append(line).append("\n");
            }
            br.close();

            _is.close();
            con.disconnect();
        }
        catch(IOException e) {
            respCode = 0;
            respException = e.toString();
            System.out.println(name + " " + respException);
        }
        finally {
            timespan = System.currentTimeMillis() - started;
            return buff.toString();
        }
    }
}
