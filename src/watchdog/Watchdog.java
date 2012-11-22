package watchdog;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 *
 * @author kit
 */
public class Watchdog extends DefaultHandler {
    
    private class DisabledBySchedule extends SAXException {
        public DisabledBySchedule(String s) {
            super(s);
        }
    }
    
    private class CustomPattern {
        private String name;
        private boolean found;
        
        public CustomPattern(String name) {
            this.name = name;
        }
        
        public void printMe() {
            StringBuilder result = new StringBuilder("MATCH \"");
            result.append(name).append((found)? "\" FOUND" : "\" NOT FOUND!!! ");
            System.out.println(result.toString());
            report.append("<tr style = \"background-color: ")
                .append((evenodd)? "lightgray" : "white");
            if(!found) {
                report.append("; font-weight: bold; color: red");
            }
            report.append(";\"><td style = \"padding: 0.3em; border: black solid 1px;\" colspan = \"3\">MATCH ")
                .append(encodeHTML(name))
                .append("</td><td style = \"padding: 0.3em; border: black solid 1px;\">")
                .append((found)? "Passed" : "Failed")
                .append("</td></tr>\n");
            evenodd = !evenodd;
        }
        
        public void find() {
            Pattern p = Pattern.compile(name);
            Matcher m = p.matcher(getLastResponse());
            if(m.find()) {
                responses.add(m.group());
                found = true;
            }
            else {
                saveResponse(".txt");
                responses.add("");
                found = false;
                error = true;
            }
        }
    }
    
    private static class Request {
        public static int timeout;
        public static String useragent;
        public long timespan;
        public String name;
        public String method;
        public int respCode;
        public String respMessage;
        public String respException;

        public Request(String name) {
            this.name = name;
        }
        
        public boolean isRespOK() {
            return (respCode == 200 || respCode == 204)? true : false;
        }

        public String get(String type) {
            URL url;
            HttpURLConnection con;
            String line;
            InputStream _is;
            method = "GET";
            StringBuilder buff = new StringBuilder();
            long started = System.currentTimeMillis();
            try {
                url = new URL(name);
                con = (HttpURLConnection)url.openConnection();
                con.setConnectTimeout(timeout);
                con.setReadTimeout(timeout);
                con.setRequestMethod(method);
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
                respMessage = null;
                respException = e.toString();
                System.out.println(name + " " + respException);
            }
            finally {
                timespan = System.currentTimeMillis() - started;
                return buff.toString();
            }
        }

        public String post(String message, String type) {
            URL url;
            HttpURLConnection con;
            String line;
            InputStream _is;
            method = "POST";
            StringBuilder buff = new StringBuilder();
            long started = System.currentTimeMillis();
            try {
                url = new URL(name);
                con = (HttpURLConnection)url.openConnection();
                con.setRequestMethod(method);
                con.setDoOutput(true);
                con.setConnectTimeout(timeout);
                con.setReadTimeout(timeout);
                con.setRequestProperty("Connection", "close");
                con.setRequestProperty("Content-Type", type);
                con.setRequestProperty("Content-Length", Integer.toString(message.getBytes().length));
                con.setRequestProperty("Content-Language", "en-US");
                con.addRequestProperty("User-Agent", useragent);
                //con.setUseCaches(true);
                DataOutputStream dos = new DataOutputStream(con.getOutputStream());
                dos.writeBytes(message);
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
                respMessage = null;
                respException = e.toString();
                System.out.println(name + " " + respException);
            }
            finally {
                timespan = System.currentTimeMillis() - started;
                return buff.toString();
            }
        }
        
        public void printMe() {
            StringBuilder sb = new StringBuilder(method);
            sb.append(" ").append(name).append(" (").append(timespan).append("ms) ");
            if(respCode == 0) {
                sb.append(respException);
            }
            else {
                sb.append(respCode).append(": ").append(respMessage);
            }
            System.out.println(sb.toString());
            report.append("<tr style = \"background-color: ")
                .append((evenodd)? "lightgray" : "white");
            if(!isRespOK()) {
                report.append("; font-weight: bold; color: red");
            }
            report.append(";\"><td style = \"padding: 0.3em; border: black solid 1px;\">")
                .append(method).append(" ")
                .append(name).append("</td><td style = \"padding: 0.3em; border: black solid 1px;\">")
                .append(timespan).append(" ms")
                .append("</td><td style = \"padding: 0.3em; border: black solid 1px;\">")
                .append((respCode == 0)? respException : Integer.toString(respCode) + ": " + respMessage)
                .append("</td><td style = \"padding: 0.3em; border: black solid 1px;\">")
                .append((isRespOK())? "Passed" : "Failed")
                .append("</td></tr>\n");
            evenodd = !evenodd;
            if(respCode != 200) {
                error = true;
            }
        }
    }
    
    private String DATE_FORMAT_NOW;
    private String logsFolder;
    private String currentFolder;
    private boolean removeresponses;
    private int attempts;
    private List<String> paths;
    private Request lastRequest;
    private String lastFullPath;
    private CustomPattern lastMatching;
    private List<Request> requests;
    private List<String> responses;
    private List<String> files;
    private String started;
    private static String logFile;
    private static String reportFile;
    private static String paramsFile;
    private PrintStream newPrintStream;
    private static StringBuilder report;
    private static boolean evenodd;
    private static boolean error;
    private String mailsubject;
    private String mailserver;
    private String mailport;
    private String mailauth;
    private String mailtransport;
    private String mailuser;
    private String mailpassword;
    private String mailfrom;
    private String mailto;
    private String mailcc;
    private boolean mail;
    
    private String encodeHTML(String s) {
        char c;
        StringBuilder out = new StringBuilder();
        for(int i = 0; i < s.length(); i++) {
            c = s.charAt(i);
            if(c > 127 || c == '"' || c == '<' || c=='>') {
                out.append("&#").append(Integer.toString(c)).append(";");
            }
            else {
                out.append(c);
            }
        }
        return out.toString();
    }
    
    private void send() {
        Multipart mp;
        MimeBodyPart mbp;
        FileDataSource fds;
        try {
            Properties props = new Properties();
            props.put("mail.smtp.host", mailserver);
            props.put("mail.smtp.port", mailport);
            props.put("mail.smtp.auth", mailauth);
            Session session = Session.getDefaultInstance(props, null);
            // Construct the message
            Message msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(mailfrom));
            msg.addRecipient(Message.RecipientType.TO, new InternetAddress(mailto));
            msg.addRecipient(Message.RecipientType.CC, new InternetAddress(mailcc));
            msg.setSubject(mailsubject + " " + started);
            //Message body
            mp = new MimeMultipart();
            // Attachments
            for(String s : files) {
                mbp = new MimeBodyPart();
                fds = new FileDataSource(s);
                mbp.setDataHandler(new DataHandler(fds));
                mbp.setFileName(fds.getName());
                mbp.setHeader("Content-Transfer-Encoding", "base64");
                mbp.setHeader("Content-Type", "text/html; charset=UTF-8");
                mp.addBodyPart(mbp);
            }
            // Report
            mbp = new MimeBodyPart();
            mbp.setContent(report.toString(), "text/html; charset=UTF-8");
            mbp.setHeader("Content-Transfer-Encoding", "base64");
            mp.addBodyPart(mbp);
            //
            msg.setContent(mp);
            Transport tran = session.getTransport(mailtransport);
            tran.connect(mailserver, mailuser, mailpassword);
            tran.sendMessage(msg, msg.getAllRecipients());
            tran.close();
            System.out.println("EMAIL SENT");
        }
        catch(Exception e) {
            System.out.println(e.toString());
        }
    }
    
    private void saveResponse(String ext) {
        File logs, curDir;
        StringBuilder p = new StringBuilder();
        try {            
            logs = new File(logsFolder);
            if(!logs.exists()){
                if(!logs.mkdir()){
                    System.out.println("Unable to create Log folder!");
                    return;
                }
            }
            p.append(logsFolder).append("/").append(started.replaceAll("[:\\s]", "_"));
            currentFolder = p.toString();
            curDir = new File(currentFolder);
            if(!curDir.exists()){
                if(!curDir.mkdir()) {
                    System.out.println("Unable to create Log's sub-folder!");
                    return;
                }
            }
            p.append("/").append(lastRequest.name.replace("http://", "").
                                            replace("https://", "").replaceAll("[\\?/:]", "_")).append(ext);
            BufferedWriter out = new BufferedWriter(new FileWriter(p.toString()));
            out.write(getLastResponse());
            out.flush();
            out.close();
            files.add(p.toString());
            System.out.println(p.toString() + " SAVED");
        }
        catch (Exception e) {
            System.out.println(e.toString());
        }
    }
    
    private void removeResponses() {
        File file;
        for(String f : files) {
            System.out.println("Deleting " + f);
            file = new File(f);
            file.delete();
        }
        if(currentFolder != null) {
            System.out.println("Deleting " + currentFolder);
            file = new File(currentFolder);
            file.delete();
        }
    }
    
    private String getLastResponse() {
        return ((responses.size() > 0)? responses.get(responses.size() - 1) : "");
    }
    
    private void decLastResponse() {
        if(responses.size() > 0) {
            responses.remove(responses.size() - 1);
        }
    }
    
    private void loadConstants(Attributes attrs) {
        DATE_FORMAT_NOW = attrs.getValue("DATE_FORMAT_NOW");
        logsFolder = attrs.getValue("logsfolder");
        removeresponses = Boolean.parseBoolean(attrs.getValue("removeresponses"));
        attempts = Integer.parseInt(attrs.getValue("attempts"));
        Request.timeout = Integer.parseInt(attrs.getValue("timeout"));
        Request.useragent = attrs.getValue("useragent");
        mail = Boolean.parseBoolean(attrs.getValue("mail"));
        mailsubject = attrs.getValue("mailsubject");
        mailserver = attrs.getValue("mailserver");
        mailport = attrs.getValue("mailport");
        mailauth = attrs.getValue("mailauth");
        mailtransport = attrs.getValue("mailtransport");
        mailuser = attrs.getValue("mailuser");
        mailpassword = attrs.getValue("mailpassword");
        mailfrom = attrs.getValue("mailfrom");
        mailto = attrs.getValue("mailto");
        mailcc = attrs.getValue("mailcc");
    }
    
    private String getFullPath() {
        StringBuilder sb = new StringBuilder();
        for(String p : paths) {
            sb.append(p);
        }
        return sb.toString();
    }
    
    private void incFullPath(String name) {
        paths.add(name);
        lastFullPath = getFullPath();
    }
    
    private void decFullPath() {
        paths.remove(paths.size() - 1);
        lastFullPath = getFullPath();
    }
    
    private String now() {
        return now(DATE_FORMAT_NOW);
    }
    
    private String now(String dateFormat) {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);
        return sdf.format(cal.getTime());
    }
    
    private void prepareExecution() {
        requests = new ArrayList<Request>();
        paths = new ArrayList<String>();
        responses = new ArrayList<String>();
        files = new ArrayList<String>();
        report = new StringBuilder();
        report.append("<!DOCTYPE html>\n<html>\n<head>\n")
            .append("<title>").append(mailsubject).append(" ").append(started).append("</title>\n")
            .append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">\n</head>\n<body>\n")
            .append("<div>\n<table style = \"font-family: arial; font-style: normal; ")
            .append("font-size: 0.7em; width: 100%; padding: 0.5em; ")
            .append("border: black solid 1px; border-collapse: collapse;\">\n")
            .append("<tr style = \"background-color: lightgray;\">")
            .append("<th style = \"padding: 0.3em; width: 60%; border: black solid 1px;\">Operation")
            .append("</th><th style = \"padding: 0.3em; width: 8%; border: black solid 1px;\">Timing")
            .append("</th><th style = \"padding: 0.3em; width: 21%; border: black solid 1px;\">Response")
            .append("</th><th style = \"padding: 0.3em; width: 11%; border: black solid 1px;\">Result")
            .append("</th></tr>\n");
        CookieManager cm = new CookieManager();
        cm.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        CookieHandler.setDefault(cm);
        if(logFile != null) {
            File logs = new File(logsFolder);
            if(!logs.exists()) {
                logs.mkdir();
            }
            File f = new File(logsFolder + "/" + logFile);
            try {
                newPrintStream = new PrintStream(new FileOutputStream(f, true));
                System.setOut(newPrintStream);
            }
            catch(Exception ex) {
                System.out.println(ex.toString());
                System.exit(1);
            }
        }
    }
    
    private void logIn(String user, String password) {
        if(lastRequest.respCode == 200) {
            String response = getLastResponse();
            String viewStateNameDelimiter = "__VIEWSTATE";
            String valueDelimiter = "value=\"";
            int viewStateNamePosition = response.indexOf(viewStateNameDelimiter);
            int viewStateValuePosition = response.indexOf(valueDelimiter, viewStateNamePosition);
            if(0 < viewStateNamePosition && viewStateNamePosition < viewStateValuePosition) {
                int viewStateStartPosition = viewStateValuePosition + valueDelimiter.length();
                int viewStateEndPosition = response.indexOf("\"", viewStateStartPosition);
                String message = String.format(
                    "__VIEWSTATE=%1$s&Login1$UserName=%2$s&Login1$Password=%3$s&Login1$LoginButton=Sign+In",
                    response.substring(viewStateStartPosition, viewStateEndPosition), 
                    user, 
                    password);
                doPost(message, "application/x-www-form-urlencoded");
            }
        }
    }
    
    private void doOpen() {
        doGet("text/html; charset=utf-8");
    }
    
    private void doGet(String type) {
        String response = "";
        for(int i = 0; i < attempts ; i++) {
            lastRequest = new Request(lastFullPath);
            response = lastRequest.get(type);
            if(lastRequest.respCode != 0) {
                break;
            }
            pause();
        }
        requests.add(lastRequest);
        responses.add(response);
    }
    
    private void doPost(String message) {
        doPost(message, "application/json; charset=utf-8");
    }
    
    private void doPost(String message, String type) {
        String response = "";
        for(int i = 0; i < attempts ; i++) {
            lastRequest = new Request(lastFullPath);
            response = lastRequest.post(message, type);
            if(lastRequest.respCode != 0) {
                break;
            }
            pause();
        }
        requests.add(lastRequest);
        responses.add(response);
    }
    
    private void doMatch(String pt) {
        lastMatching = new CustomPattern(pt);
        lastMatching.find();
    }
    
    private void saveReport() {
        File logs;
        StringBuilder p = new StringBuilder();
        try {            
            logs = new File(logsFolder);
            if(!logs.exists()){
                if(!logs.mkdir()){
                    System.out.println("Unable to create Log folder!");
                    return;
                }
            }
            p.append(logsFolder).append("/").append(reportFile);
            BufferedWriter out = new BufferedWriter(new FileWriter(p.toString()));
            out.write(report.toString());
            out.flush();
            out.close();
            System.out.println(p.toString() + " SAVED");
        }
        catch(Exception e) {
            System.out.println(e.toString());
        }
    }
    
    @Override
    public void startElement(String uri, String localName, String qName, Attributes attrs) throws SAXException {
        if("constants".equals(qName)) {
            loadConstants(attrs);
        }
        else if("execute".equals(qName)) {
            started = now();
            prepareExecution();
            System.out.println("Started: " + started);
            incFullPath("");
        }
        else if("disable".equals(qName)) {
            String from = attrs.getValue("from");
            String till = attrs.getValue("till");
            String current = now("HH:mm");
            if(0 <= current.compareTo(from) && current.compareTo(till) <= 0) {
                throw new DisabledBySchedule("Aborted by Schedule from " + from + " till " + till);
            }  
        }
        else if("set".equals(qName)) {
            incFullPath(attrs.getValue("name"));
        }
        else if("open".equals(qName)) {
            incFullPath(attrs.getValue("name"));
            doOpen();
            lastRequest.printMe();
        }
        else if("post".equals(qName)) {
            incFullPath(attrs.getValue("name"));
            doPost(attrs.getValue("message"));
            lastRequest.printMe();
        }
        else if("user".equals(qName)) {
            logIn(attrs.getValue("name"), attrs.getValue("password"));
            lastRequest.printMe();
        }
        else if("find".equals(qName)) {
            if(lastRequest.isRespOK()) {
                doMatch(attrs.getValue("name"));
                lastMatching.printMe();
            }
        }
    }
    
    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if("set".equals(qName)) {
            decFullPath();
        }
        if("open".equals(qName)) {
            decLastResponse();
            decFullPath();
        }
        else if("post".equals(qName)) {
            decLastResponse();
            decFullPath();
        }
        else if("user".equals(qName)) {
            decLastResponse();
        }
        else if("find".equals(qName)) {
            decLastResponse();
        }
        else if("execute".equals(qName)) {
            report.append("</table>\n</div>\n</body>\n</html>\n");
            if(mail && error) {
                send();
            }
            if(reportFile != null) {
                saveReport();
            }
            if(removeresponses) {
                removeResponses();
            }
            System.out.println(((error)? "Finished with error(s): " : "Finished: ") + now());
            System.out.println();
        }
    }
    
    private void pause() {
        try {
            Thread.sleep(Request.timeout);
        }
        catch (InterruptedException ex) {
            System.out.println(ex.toString());
            System.exit(1);
        }
    }
    
    private static void parseArgs(String[] args) {
        boolean valid = true;
        int i = 0;
        while(i < args.length - 1) {
            if(args[i].equals("-log")) {
                logFile = args[i + 1];
            }
            else if(args[i].equals("-out")) {
                reportFile = args[i + 1];
            }
            else if(args[i].equals("-params")) {
                paramsFile = args[i + 1];
            }
            else {
                valid = false;
                break;
            }
            i+=2;
        }
        if(!valid || paramsFile == null) {
            System.out.println("java -jar Watchdog.jar -params parameters.xml [-log log.txt] [-out report.htm]");
            System.exit(1);            
        }
    }
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        parseArgs(args);
        try {
            File xmlFile = new File(paramsFile);
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser parser = factory.newSAXParser();
            DefaultHandler handler = new Watchdog();
            parser.parse(xmlFile, handler);
        }
        catch(DisabledBySchedule ex) {
            System.out.println(ex.getMessage());
            System.out.println();
        }
        catch(Exception ex) {
            System.out.println(ex.toString());
        }
    }
}
