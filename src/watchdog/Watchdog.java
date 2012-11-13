package watchdog;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.PrintStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
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
        public String name;
        public boolean found;
        
        public CustomPattern(String name) {
            this.name = name;
        }
    }
    
    private class CustomRequest extends Request {
        
        public CustomRequest(String name) {
            super(name);
        }
        
        public void printMe() {
            StringBuilder sb = new StringBuilder((operation == Operations.READING)? "GET " : "POST ");
            sb.append(name).append(" (").append(timespan).append("ms)");
            if(respCode != 0) {
                sb.append(" ").append(respCode).append(":").append(respMessage);
            }
            if(!"".equals(respException)) {
                sb.append(" ").append(respException);
            }
            System.out.println(sb.toString());
            report.append("<tr style = \"background-color: ")
                .append((evenodd)?"lightgray":"white")
                .append((respCode != 200)?"; font-weight: bold; color: red":"")
                .append(";\"><td style = \"padding: 0.3em; border: black solid 1px;\">")
                .append((operation == Operations.READING)? "Getting " : "Posting ")
                .append(name).append("</td><td style = \"padding: 0.3em; border: black solid 1px;\">")
                .append(timespan).append(" ms")
                .append("</td><td style = \"padding: 0.3em; border: black solid 1px;\">")
                .append((respCode == 0)? respException : Integer.toString(respCode) + ": ").append(respMessage)
                .append("</td><td style = \"padding: 0.3em; border: black solid 1px;\">")
                .append((respCode == 0)? "Failed" : "Passed")
                .append("</td></tr>\n");
            evenodd = !evenodd;
            if(respCode != 200) {
                error = true;
            }
        }
    }
    
    private String DATE_FORMAT_NOW;
    private String logsfolder;
    private String currentFolder;
    private boolean removeresponses;
    private int attempts;
    private List<String> paths;
    private CustomRequest lastRequest;
    private String lastFullPath;
    private List<CustomRequest> requests;
    private List<String> responses;
    private List<String> files;
    private String started;
    private static String logfile;
    private PrintStream newPrintStream;
    private StringBuilder report;
    boolean evenodd = true;
    boolean error;
    private String mailsubject;
    private String mailserver;
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
            props.put("mail.smtps.auth", "true");
            Session session = Session.getDefaultInstance(props, null);
            // Construct the message
            Message msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(mailfrom));
            msg.addRecipient(Message.RecipientType.TO, new InternetAddress(mailto));
            msg.addRecipient(Message.RecipientType.CC, new InternetAddress(mailcc));
            msg.setSubject(mailsubject + " " + started);
            // Attachments
            mp = new MimeMultipart();
            for(String s : files) {
                mbp = new MimeBodyPart();
                fds = new FileDataSource(s);
                mbp.setDataHandler(new DataHandler(fds));
                mbp.setFileName(fds.getName());
                mbp.setHeader("Content-Transfer-Encoding", "base64");
                mbp.setHeader("Content-Type", "text/html; charset=UTF-8");
                mp.addBodyPart(mbp);
            }
            // Table
            mbp = new MimeBodyPart();
            mbp.setContent(report.toString(), "text/html; charset=UTF-8");
            mbp.setHeader("Content-Transfer-Encoding", "base64");
            mp.addBodyPart(mbp);
            //
            msg.setContent(mp);
            Transport tran = session.getTransport("smtps");
            tran.connect(mailserver, mailuser, mailpassword);
            tran.sendMessage(msg, msg.getAllRecipients());
            tran.close();
            System.out.println("Email sent");
        }
        catch(Exception e) {
            System.out.println(e.toString());
        }
    }
    
    private void saveResponse(String ext) {
        File logs, curDir;
        StringBuilder p = new StringBuilder();
        try {            
            logs = new File(logsfolder);
            if(!logs.exists()){
                if(!logs.mkdir()){
                    System.out.println("Unable to create Log folder!");
                    return;
                }
            }
            p.append(logsfolder).append("/").append(started.replaceAll("[:\\s]", "_"));
            currentFolder = p.toString();
            curDir = new File(currentFolder);
            if(!curDir.exists()){
                if(!curDir.mkdir()) {
                    System.out.println("Unable to create Log's sub-folder!");
                    return;
                }
            }
            p.append("/").append(lastRequest.name.replace("http://", "").
                                            replace("https://", "").replace("/", "_")).append(ext);
            BufferedWriter out = new BufferedWriter(new FileWriter(p.toString()));
            out.write(getLastResponse());
            out.flush();
            out.close();
            files.add(p.toString());
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
        if(responses.size() > 0) {
            return responses.get(responses.size() - 1);
        }
        else {
            return "";
        }
    }
    
    private void decLastResponse() {
        if(responses.size() > 0) {
            responses.remove(responses.size() - 1);
        }
    }
    
    private void loadConstants(Attributes attrs) {
        DATE_FORMAT_NOW = attrs.getValue("DATE_FORMAT_NOW");
        logsfolder = attrs.getValue("logsfolder");
        removeresponses = Boolean.parseBoolean(attrs.getValue("removeresponses"));
        attempts = Integer.parseInt(attrs.getValue("attempts"));
        Request.timeout = Integer.parseInt(attrs.getValue("timeout"));
        Request.useragent = attrs.getValue("useragent");
        mail = Boolean.parseBoolean(attrs.getValue("mail"));
        mailsubject = attrs.getValue("mailsubject");
        mailserver = attrs.getValue("mailserver");
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
        requests = new ArrayList<CustomRequest>();
        paths = new ArrayList<String>();
        responses = new ArrayList<String>();
        files = new ArrayList<String>();
        report = new StringBuilder();
        report.append("<!DOCTYPE html>\n<html>\n<head>\n")
            .append("<title>Auto Monitoring Report ").append(started).append("</title>\n")
            .append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">\n</head>\n<body>\n")
            .append("<div>\n<table style = \"font-family: arial; font-style: normal; ")
            .append("font-size: 0.7em; width: 100%; padding: 0.5em; ")
            .append("border: black solid 1px; border-collapse: collapse;\">\n")
            .append("<tr style = \"background-color: white;\">")
            .append("<th style = \"padding: 0.3em; width: 60%; border: black solid 1px;\">Operation")
            .append("</th><th style = \"padding: 0.3em; width: 8%; border: black solid 1px;\">Timing")
            .append("</th><th style = \"padding: 0.3em; width: 21%; border: black solid 1px;\">Response")
            .append("</th><th style = \"padding: 0.3em; width: 11%; border: black solid 1px;\">Result")
            .append("</th></tr>\n");
        CookieManager cm = new CookieManager();
        cm.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        CookieHandler.setDefault(cm);
        if(logfile != null) {
            File logs = new File(logsfolder);
            if(!logs.exists()) {
                logs.mkdir();
            }
            File f = new File(logsfolder + "/" + logfile);
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
                String post = String.format(
                    "__VIEWSTATE=%1$s&Login1$UserName=%2$s&Login1$Password=%3$s&Login1$LoginButton=Sign+In",
                    response.substring(viewStateStartPosition, viewStateEndPosition), 
                    user, 
                    password);
                doWrite(post, "application/x-www-form-urlencoded");
            }
        }
    }
    
    private void doRead() {
        doRead("application/x-www-form-urlencoded");
    }
    
    private void doRead(String type) {
        String response = "";
        for(int i = 0; i < attempts ; i++) {
            lastRequest = new CustomRequest(lastFullPath);
            response = lastRequest.get(type);
            if(lastRequest.respCode != 0) {
                break;
            }
            pause();
        }
        requests.add(lastRequest);
        responses.add(response);
    }
    
    private void doWrite(String post) {
        doWrite(post, "application/json");
    }
    
    private void doWrite(String post, String type) {
        String response = "";
        for(int i = 0; i < attempts ; i++) {
            lastRequest = new CustomRequest(lastFullPath);
            response = lastRequest.post(post, type);
            if(lastRequest.respCode != 0) {
                break;
            }
            pause();
        }
        requests.add(lastRequest);
        responses.add(response);
    }
    
    private void doMatch(String pt) {
        StringBuilder result = new StringBuilder("MATCH \"" + pt + "\" ");
        CustomPattern cp = new CustomPattern(pt);
        Pattern p = Pattern.compile(pt);
        Matcher m = p.matcher(getLastResponse());
        if(m.find()) {
            String r = m.group();
            responses.add(r);
            cp.found = true;
            result.append("FOUND");
        }
        else {
            saveResponse((lastRequest.operation == Request.Operations.READING)? ".htm" : ".txt");
            responses.add("");
            cp.found = false;
            result.append("NOT FOUND!!!");
        }
        System.out.println(result.toString());
        report.append("<tr style = \"background-color: ")
            .append((evenodd)? "lightgray" : "white").append((!cp.found)? "; font-weight: bold; color: red" : "")
            .append(";\"><td style = \"padding: 0.3em; border: black solid 1px;\" colspan = \"3\">Matching ")
            .append(encodeHTML(cp.name))
            .append("</td><td style = \"padding: 0.3em; border: black solid 1px;\">")
            .append((cp.found)? "Passed" : "Failed")
            .append("</td></tr>\n");
        evenodd = !evenodd;
        if(!cp.found) {
            error = true;
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
        else if("read".equals(qName)) {
            incFullPath(attrs.getValue("name"));
            doRead();
            lastRequest.printMe();
        }
        else if("write".equals(qName)) {
            incFullPath(attrs.getValue("name"));
            doWrite(attrs.getValue("post"));
            lastRequest.printMe();
        }
        else if("user".equals(qName)) {
            logIn(attrs.getValue("name"), attrs.getValue("password"));
            lastRequest.printMe();
        }
        else if("find".equals(qName)) {
            if(lastRequest.respCode == 200) {
                doMatch(attrs.getValue("name"));
            }
        }
    }
    
    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if("set".equals(qName)) {
            decFullPath();
        }
        if("read".equals(qName)) {
            decLastResponse();
            decFullPath();
        }
        else if("write".equals(qName)) {
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
            if(removeresponses) {
                removeResponses();
            }
            System.out.println("Finished: " + now());
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
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        if(args.length == 0 || args.length > 2) {
            System.out.println("java -jar Watchdog.jar parameters.xml [log.txt]");
            System.exit(1);
        }
        if(args.length == 2) {
            logfile = args[1];
        }
        try {
            File xmlFile = new File(args[0]);
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
