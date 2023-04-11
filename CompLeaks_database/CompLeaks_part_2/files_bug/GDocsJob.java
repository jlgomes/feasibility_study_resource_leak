package com.mendhak.gpslogger.senders.gdocs;

import android.os.Build;
import com.mendhak.gpslogger.common.Utilities;
import com.mendhak.gpslogger.common.events.GDocsEvent;
import com.path.android.jobqueue.Job;
import com.path.android.jobqueue.Params;
import de.greenrobot.event.EventBus;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class GDocsJob extends Job {
    private static final org.slf4j.Logger tracer = LoggerFactory.getLogger(GDocsJob.class.getSimpleName());
    String token;
    File gpxFile;

    protected GDocsJob(String token, File gpxFile) {
        super(new Params(1).requireNetwork().persist());
        this.token = token;
        this.gpxFile = gpxFile;

    }

    @Override
    public void onAdded() {

    }

    @Override
    public void onRun() throws Throwable {
        tracer.debug("Running GDocsJob item " + this.getRunGroupId());
        FileInputStream fis = new FileInputStream(gpxFile);
        String fileName = gpxFile.getName();

        String gpsLoggerFolderId = GetFileIdFromFileName(token, "GPSLogger For Android");

        if (Utilities.IsNullOrEmpty(gpsLoggerFolderId)) {
            //Couldn't find folder, must create it
            gpsLoggerFolderId = CreateEmptyFile(token, "GPSLogger For Android", "application/vnd.google-apps.folder", "root");

            if (Utilities.IsNullOrEmpty(gpsLoggerFolderId)) {
                EventBus.getDefault().post(new GDocsEvent(false));
                return;
            }
        }

        //Now search for the file
        String gpxFileId = GetFileIdFromFileName(token, fileName);

        if (Utilities.IsNullOrEmpty(gpxFileId)) {
            //Create empty file first
            gpxFileId = CreateEmptyFile(token, fileName, GetMimeTypeFromFileName(fileName), gpsLoggerFolderId);

            if (Utilities.IsNullOrEmpty(gpxFileId)) {
                EventBus.getDefault().post(new GDocsEvent(false));
                return;
            }
        }

        if (!Utilities.IsNullOrEmpty(gpxFileId)) {
            //Set file's contents
            UpdateFileContents(token, gpxFileId, Utilities.GetByteArrayFromInputStream(fis), fileName);
        }
        EventBus.getDefault().post(new GDocsEvent(true));
    }

    private String UpdateFileContents(String authToken, String gpxFileId, byte[] fileContents, String fileName) {
        HttpURLConnection conn = null;
        String fileId = null;

        String fileUpdateUrl = "https://www.googleapis.com/upload/drive/v2/files/" + gpxFileId + "?uploadType=media";

        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.FROYO) {
                //Due to a pre-froyo bug
                //http://android-developers.blogspot.com/2011/09/androids-http-clients.html
                System.setProperty("http.keepAlive", "false");
            }

            URL url = new URL(fileUpdateUrl);

            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PUT");
            conn.setRequestProperty("User-Agent", "GPSLogger for Android");
            conn.setRequestProperty("Authorization", "Bearer " + authToken);
            conn.setRequestProperty("Content-Type", GetMimeTypeFromFileName(fileName));
            conn.setRequestProperty("Content-Length", String.valueOf(fileContents.length));

            conn.setUseCaches(false);
            conn.setDoInput(true);
            conn.setDoOutput(true);

            DataOutputStream wr = new DataOutputStream(
                    conn.getOutputStream());
            wr.write(fileContents);
            wr.flush();
            wr.close();

            String fileMetadata = Utilities.GetStringFromInputStream(conn.getInputStream());

            JSONObject fileMetadataJson = new JSONObject(fileMetadata);
            fileId = fileMetadataJson.getString("id");
            tracer.debug("File updated : " + fileId);

        } catch (Exception e) {
            System.out.println(e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }

        return fileId;
    }

    private String CreateEmptyFile(String authToken, String fileName, String mimeType, String parentFolderId) {

        String fileId = null;
        HttpURLConnection conn = null;

        String createFileUrl = "https://www.googleapis.com/drive/v2/files";

        String createFilePayload = "   {\n" +
                "             \"title\": \"" + fileName + "\",\n" +
                "             \"mimeType\": \"" + mimeType + "\",\n" +
                "             \"parents\": [\n" +
                "              {\n" +
                "               \"id\": \"" + parentFolderId + "\"\n" +
                "              }\n" +
                "             ]\n" +
                "            }";

        try {

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.FROYO) {
                //Due to a pre-froyo bug
                //http://android-developers.blogspot.com/2011/09/androids-http-clients.html
                System.setProperty("http.keepAlive", "false");
            }

            URL url = new URL(createFileUrl);

            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("User-Agent", "GPSLogger for Android");
            conn.setRequestProperty("Authorization", "Bearer " + authToken);
            conn.setRequestProperty("Content-Type", "application/json");

            conn.setUseCaches(false);
            conn.setDoInput(true);
            conn.setDoOutput(true);

            DataOutputStream wr = new DataOutputStream(
                    conn.getOutputStream());
            wr.writeBytes(createFilePayload);
            wr.flush();
            wr.close();

            fileId = null;

            String fileMetadata = Utilities.GetStringFromInputStream(conn.getInputStream());

            JSONObject fileMetadataJson = new JSONObject(fileMetadata);
            fileId = fileMetadataJson.getString("id");
            tracer.debug("File created with ID " + fileId + " of type " + mimeType);

        } catch (Exception e) {

            System.out.println(e.getMessage());
            System.out.println(e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }

        }

        return fileId;
    }


    private String GetFileIdFromFileName(String authToken, String fileName) {

        HttpURLConnection conn = null;
        String fileId = "";

        try {

            fileName = URLEncoder.encode(fileName, "UTF-8");
            String searchUrl = "https://www.googleapis.com/drive/v2/files?q=title%20%3D%20%27" + fileName + "%27%20and%20trashed%20%3D%20false";


            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.FROYO) {
                //Due to a pre-froyo bug
                //http://android-developers.blogspot.com/2011/09/androids-http-clients.html
                System.setProperty("http.keepAlive", "false");
            }

            URL url = new URL(searchUrl);

            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "GPSLogger for Android");
            conn.setRequestProperty("Authorization", "OAuth " + authToken);

            String fileMetadata = Utilities.GetStringFromInputStream(conn.getInputStream());


            JSONObject fileMetadataJson = new JSONObject(fileMetadata);
            if (fileMetadataJson.getJSONArray("items") != null && fileMetadataJson.getJSONArray("items").length() > 0) {
                fileId = fileMetadataJson.getJSONArray("items").getJSONObject(0).get("id").toString();
                tracer.debug("Found file with ID " + fileId);
            }

        } catch (Exception e) {
            tracer.error("SearchForGPSLoggerFile", e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }

        return fileId;
    }

    private String GetMimeTypeFromFileName(String fileName) {
        if (fileName.endsWith("kml")) {
            return "application/vnd.google-earth.kml+xml";
        }

        if (fileName.endsWith("gpx")) {
            return "application/gpx+xml";
        }

        if (fileName.endsWith("zip")) {
            return "application/zip";
        }

        if (fileName.endsWith("xml")) {
            return "application/xml";
        }

        if (fileName.endsWith("nmea")) {
            return "text/plain";
        }

        return "application/vnd.google-apps.spreadsheet";
    }

    @Override
    protected void onCancel() {
        EventBus.getDefault().post(new GDocsEvent(false));
    }

    @Override
    protected boolean shouldReRunOnThrowable(Throwable throwable) {
        tracer.error("Could not upload to Google Drive", throwable);
        return false;
    }
}