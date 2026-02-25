package com.stevecrew.callloganalyzer;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CSVExporter {

    public static String exportToCSV(Context context, List<CallLogEntry> calls) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "CallLog_" + timestamp + ".csv";
        
        StringBuilder csv = new StringBuilder();
        csv.append("Date/Time,Number,Contact Name,Type,Duration (seconds)\n");
        
        for (CallLogEntry entry : calls) {
            csv.append(escapeCSV(entry.getFormattedDate())).append(",");
            csv.append(escapeCSV(entry.getNumber())).append(",");
            csv.append(escapeCSV(entry.getContactName())).append(",");
            csv.append(escapeCSV(entry.getTypeString())).append(",");
            csv.append(entry.getDuration()).append("\n");
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ use MediaStore
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                values.put(MediaStore.Downloads.MIME_TYPE, "text/csv");
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    OutputStream os = context.getContentResolver().openOutputStream(uri);
                    if (os != null) {
                        os.write(csv.toString().getBytes());
                        os.close();
                        return "Downloads/" + fileName;
                    }
                }
            } else {
                // Android 9 and below
                File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File file = new File(downloadsDir, fileName);
                FileOutputStream fos = new FileOutputStream(file);
                fos.write(csv.toString().getBytes());
                fos.close();
                return file.getAbsolutePath();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return null;
    }

    private static String escapeCSV(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
