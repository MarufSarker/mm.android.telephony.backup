package mm.android.telephony.backup;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.CallLog;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity
{
    AppCompatButton   ui_select_storage    = null;
    AppCompatTextView ui_selected_storage  = null;
    AppCompatButton   ui_backup_sms        = null;
    AppCompatTextView ui_backup_sms_status = null;
    AppCompatTextView ui_log_text          = null;
    AppCompatButton   ui_backup_call_logs  = null;

    @Override protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        request_permissions(
            Arrays.asList(Manifest.permission.WRITE_EXTERNAL_STORAGE,
                          Manifest.permission.READ_EXTERNAL_STORAGE,
                          Manifest.permission.READ_SMS,
                          Manifest.permission.READ_CALL_LOG,
                          Manifest.permission.READ_CONTACTS,
                          Manifest.permission.READ_CALENDAR,
                          Manifest.permission.GET_ACCOUNTS));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            request_permissions(
                Arrays.asList(Manifest.permission.MANAGE_EXTERNAL_STORAGE));

        ui_select_storage    = findViewById(R.id.select_storage);
        ui_selected_storage  = findViewById(R.id.selected_storage);
        ui_backup_sms        = findViewById(R.id.backup_sms);
        ui_backup_sms_status = findViewById(R.id.backup_sms_status);
        ui_backup_call_logs  = findViewById(R.id.backup_call_logs);
        ui_log_text          = findViewById(R.id.log_text);

        ui_select_storage.setOnClickListener(view -> show_storage_selector());
        ui_backup_sms.setOnClickListener(view -> backup_sms());
        ui_backup_call_logs.setOnClickListener(view -> backup_call_logs());

        ui_log_text.setTextIsSelectable(true);

        String _selected_storage = get_selected_storage();
        if (_selected_storage == null)
            show_storage_selector();
        else
            ui_selected_storage.setText(_selected_storage);
    }

    void request_permissions(List<String> permissions)
    {
        for (String permission : permissions)
        {
            if (ContextCompat.checkSelfPermission(this, permission) ==
                PackageManager.PERMISSION_GRANTED)
                continue;
            ActivityCompat.requestPermissions(
                this, new String[] {permission}, 0);
        }
    }

    void append_log(String str) { ui_log_text.append("\n" + str); }

    String get_selected_storage()
    {
        File file = new File(ContextCompat.getDataDir(getApplicationContext()),
                             "storage.txt");

        BufferedReader reader = null;
        String         txt    = null;

        try
        {
            reader = new BufferedReader(new FileReader(file));
            txt    = reader.readLine();
        }
        catch (Exception e)
        {
            e.printStackTrace();
            append_log("Failed to create reader for storage config file.\n" +
                       e.getMessage());
        }
        finally
        {
            if (reader != null)
            {
                try
                {
                    reader.close();
                }
                catch (IOException e)
                {
                    append_log("Failed to close storage config file.\n" +
                               e.getMessage());
                }
            }
        }

        return txt;
    }

    void show_storage_selector()
    {
        LinearLayoutCompat.LayoutParams params =
            new LinearLayoutCompat.LayoutParams(
                LinearLayoutCompat.LayoutParams.MATCH_PARENT,
                LinearLayoutCompat.LayoutParams.MATCH_PARENT);
        LinearLayoutCompat layout = new LinearLayoutCompat(this);
        layout.setLayoutParams(params);
        layout.setOrientation(LinearLayoutCompat.VERTICAL);

        params.height = LinearLayoutCompat.LayoutParams.WRAP_CONTENT;

        AppCompatTextView title = new AppCompatTextView(this);
        title.setLayoutParams(params);
        title.setPadding(5, 5, 5, 5);
        title.setTextAlignment(AppCompatTextView.TEXT_ALIGNMENT_CENTER);

        AppCompatTextView warning = new AppCompatTextView(this);
        warning.setLayoutParams(params);
        warning.setPadding(5, 5, 5, 5);
        warning.setTextAlignment(AppCompatTextView.TEXT_ALIGNMENT_CENTER);
        warning.setTextColor(0xffff0000);

        RadioGroup group = new RadioGroup(this);
        group.setLayoutParams(params);
        group.setPadding(5, 5, 5, 5);
        group.setTextAlignment(AppCompatTextView.TEXT_ALIGNMENT_CENTER);

        layout.addView(title);
        layout.addView(warning);
        layout.addView(group);

        ArrayList<Map<String, String>> mount_points = new ArrayList<>();
        final HashMap<Integer, String> radio_ids    = new HashMap<>();

        StorageManager storage_manager =
            this.getSystemService(StorageManager.class);

        for (StorageVolume storage_volume : storage_manager.getStorageVolumes())
        {
            HashMap<String, String> mount_point = new HashMap<>();
            mount_point.put("description", null);
            mount_point.put("directory", null);

            if (storage_volume != null)
            {
                mount_point.replace("description",
                                    storage_volume.getDescription(this));

                String path = null;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                {
                    File dir = storage_volume.getDirectory();
                    if (dir != null)
                        path = dir.toString();
                }

                if (path == null)
                {
                    try
                    {
                        @SuppressLint("DiscouragedPrivateApi")
                        Field f = StorageVolume.class.getDeclaredField("mPath");
                        f.setAccessible(true);
                        File file = ((File) f.get(storage_volume));
                        if (file != null)
                            path = file.toString();
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                    }
                }

                mount_point.replace("directory", path);
            }

            mount_points.add(mount_point);
        }

        if (mount_points.isEmpty())
            warning.setText("No Storage Found!");
        else
        {
            for (Map<String, String> mount_point : mount_points)
            {
                int         view_id     = View.generateViewId();
                String      description = mount_point.get("description");
                String      directory   = mount_point.get("directory");
                RadioButton button      = new RadioButton(this);

                radio_ids.put(view_id, directory);
                button.setId(view_id);
                button.setText(
                    String.format("%s [%s]", description, directory));

                group.addView(button);
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(layout);
        builder.setCancelable(false);

        builder.setPositiveButton("Select", (dialog, which) -> {});

        builder.setNegativeButton("Cancel", (dialog, which) -> {});

        final AlertDialog dialog = builder.create();
        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            .setOnClickListener(view -> dialog.dismiss());

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener(view -> {
                String warning_txt;
                warning.setText(null);

                int     id       = group.getCheckedRadioButtonId();
                boolean contains = radio_ids.containsKey(id);

                String path = null;
                if (contains)
                    path = radio_ids.get(id);

                if (!contains || path == null)
                {
                    warning_txt = "Select A Storage Volume";
                    warning.setText(warning_txt);
                    return;
                }

                File storage = new File(path);

                if (!storage.canWrite())
                {
                    warning_txt = "Can Not Write Into Storage Volume [" +
                                  storage.toString() + "]";
                    warning.setText(warning_txt);
                    return;
                }

                File db_dir = new File(storage, "mm/databases/");

                try
                {
                    db_dir.mkdirs();
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }

                if (!db_dir.exists())
                {
                    warning_txt =
                        "Could Not Create Directory Into Storage Volume [" +
                        db_dir.toString() + "]";
                    warning.setText(warning_txt);
                    return;
                }

                if (!db_dir.canWrite())
                {
                    warning_txt = "Could Not Write Into Directory [" +
                                  db_dir.toString() + "]";
                    warning.setText(warning_txt);
                    return;
                }

                if (db_dir.exists() && db_dir.canWrite() &&
                    db_dir.isDirectory())
                {
                    File file = new File(
                        ContextCompat.getDataDir(getApplicationContext()),
                        "storage.txt");

                    try (FileWriter writer = new FileWriter(file))
                    {
                        writer.write(storage.toString());
                        writer.flush();
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                    }

                    dialog.dismiss();
                    return;
                }

                warning_txt = "Something Went Wrong";
                warning.setText(warning_txt);
            });
    }


    File get_writeable_database_dir()
    {
        String storage = get_selected_storage();
        if (storage == null)
        {
            append_log("Storage Not Selected!");
            return null;
        }

        File dir = new File(storage, "mm/databases/");
        try
        {
            dir.mkdirs();
            return dir;
        }
        catch (Exception e)
        {
            append_log("Can't Create Directory!\n" + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }


    List<Map<String, String>> content_uri_to_list(Uri uri, String[] projection)
    {
        ContentResolver resolver = getContentResolver();

        ArrayList<Map<String, String>> rows = new ArrayList<>();

        try (Cursor cursor = resolver.query(uri, projection, null, null, null))
        {
            while (cursor.moveToNext())
            {
                HashMap<String, String> row = new HashMap<>();
                final String[] columns      = cursor.getColumnNames();

                for (String column : columns)
                {
                    String value = null;

                    try
                    {
                        final int index = cursor.getColumnIndex(column);
                        if (index == -1)
                            continue;
                        final int type = cursor.getType(index);

                        if (type == Cursor.FIELD_TYPE_NULL)
                            value = null;
                        else if (type == Cursor.FIELD_TYPE_INTEGER)
                            value = cursor.getString(index);
                        else if (type == Cursor.FIELD_TYPE_FLOAT)
                            value = cursor.getString(index);
                        else if (type == Cursor.FIELD_TYPE_STRING)
                            value = cursor.getString(index);
                        else if (type == Cursor.FIELD_TYPE_BLOB)
                        {
                            byte[] bytes = cursor.getBlob(index);
                            StringBuilder builder =
                                new StringBuilder(bytes.length * 2);
                            for (byte b : bytes)
                                builder.append(String.format("%02x", b));
                            value = builder.toString();
                        }
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                        append_log(e.getMessage());
                    }

                    row.put(column, value);
                }

                rows.add(row);
            }

            return rows;
        }
        catch (Exception e)
        {
            append_log("Failed to read content from uri!\n" + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }


    void backup_telephony(Uri content_uri,
                          String[] projection,
                          String       database_filename,
                          String       table_name,
                          String       create_table_statement,
                          List<String> expected_keys,
                          boolean      export_text)
    {
        if (content_uri == null)
        {
            append_log("Uri is null.");
            return;
        }
        if (database_filename == null)
        {
            append_log("Database filename is null.");
            return;
        }
        if (table_name == null)
        {
            append_log("Table name is null.");
            return;
        }
        if (create_table_statement == null)
        {
            append_log("Create table statement is null.");
            return;
        }
        if (expected_keys == null)
        {
            append_log("Expected keys is null.");
            return;
        }


        File dir = get_writeable_database_dir();

        if (dir == null)
        {
            append_log("No writeable directory found.");
            return;
        }

        File file = new File(dir, database_filename);

        append_log("Listing contents.");

        List<Map<String, String>> rows =
            content_uri_to_list(content_uri, projection);

        if (rows == null)
        {
            append_log("Failed to list contents.");
            return;
        }


        SQLiteDatabase db;
        try
        {
            db = SQLiteDatabase.openDatabase(
                file.toString(),
                null,
                SQLiteDatabase.OPEN_READWRITE |
                    SQLiteDatabase.CREATE_IF_NECESSARY);
        }
        catch (Exception e)
        {
            append_log("Failed to open backup database! \n" + e.getMessage());
            e.printStackTrace();
            return;
        }

        try
        {
            db.execSQL(create_table_statement);
        }
        catch (Exception e)
        {
            append_log("Failed to create database table!\n" + e.getMessage());
            return;
        }

        int _count = 0;

        StringBuilder all_obj = new StringBuilder();

        for (Map<String, String> row : rows)
        {
            StringBuilder obj = new StringBuilder();

            StringBuilder     cols   = new StringBuilder();
            StringBuilder     params = new StringBuilder();
            ArrayList<String> vals   = new ArrayList<>();

            for (Map.Entry<String, String> entry : row.entrySet())
            {
                String key   = entry.getKey();
                String value = entry.getValue();

                // parsers should use \n\n[[key]]:\n as separator
                obj.append("\n\n[[").append(key).append("]]:\n").append(value);

                if (expected_keys.contains(key))
                {
                    if (cols.length() > 0)
                    {
                        cols.append(", ");
                        params.append(", ");
                    }

                    cols.append(key);
                    params.append(":").append(key);
                    vals.add(value);
                }

                all_obj.append(key).append(": ").append(value).append(", ");
            }

            all_obj.append("\n\n");

            if (cols.length() > 0)
            {
                cols.append(", ");
                params.append(", ");
            }

            cols.append("full_object");
            params.append(":").append("full_object");
            vals.add(obj.toString());

            StringBuilder sql = new StringBuilder();
            sql.append("INSERT OR IGNORE INTO ")
                .append(table_name)
                .append(" (")
                .append(cols.toString())
                .append(") VALUES (")
                .append(params.toString())
                .append(");");

            try
            {
                db.execSQL(sql.toString(), vals.toArray());
            }
            catch (Exception e)
            {
                append_log("Failed to insert into database!\n" +
                           e.getMessage());
                e.printStackTrace();
            }

            _count += 1;
            if (_count % 5 == 0)
                append_log("Processed " + _count + " objects.");
        }

        append_log("Processed " + _count + " objects.");

        if (db.isOpen())
            db.close();

        if (export_text)
        {
            File txt = new File(dir, table_name + ".txt");

            try (FileWriter writer = new FileWriter(txt))
            {
                writer.write(all_obj.toString());
                writer.flush();
            }
            catch (Exception e)
            {
                append_log("Failed to export text!\n" + e.getMessage());
                e.printStackTrace();
            }
        }
    }




    void backup_sms()
    {
        String create_table = String.join(
            "",
            "CREATE TABLE IF NOT EXISTS sms_backup (",
            "[date] TEXT NOT NULL,",
            "[address] TEXT NOT NULL,",
            "[body] TEXT NOT NULL,",
            "[thread_id] TEXT,",
            "[date_sent] TEXT,",
            "[type] TEXT,",
            "[creator] TEXT,",
            "[read] TEXT,",
            "[subject] TEXT,",
            "[sub_id] TEXT,",
            "[reply_path_present] TEXT,",
            "[seen] TEXT,",
            "[protocol] TEXT,",
            "[person] TEXT,",
            "[service_center] TEXT,",
            "[error_code] TEXT,",
            "[_id] TEXT,",
            "[locked] TEXT,",
            "[status] TEXT,",
            "[full_object] TEXT NOT NULL,",
            "[import_created] TEXT NOT NULL",
            " DEFAULT (strftime('%Y-%m-%dT%H:%M:%S+00:00', 'now')),",
            "[import_modified] TEXT NOT NULL",
            " DEFAULT (strftime('%Y-%m-%dT%H:%M:%S+00:00', 'now')),",
            "UNIQUE([date], [address], [body], [thread_id], [type])",
            ")");

        List<String> expected_keys = Arrays.asList("date",
                                                   "address",
                                                   "body",
                                                   "thread_id",
                                                   "date_sent",
                                                   "type",
                                                   "creator",
                                                   "read",
                                                   "subject",
                                                   "sub_id",
                                                   "reply_path_present",
                                                   "seen",
                                                   "protocol",
                                                   "person",
                                                   "service_center",
                                                   "error_code",
                                                   "_id",
                                                   "locked",
                                                   "status");

        backup_telephony(Uri.parse("content://sms"),
                         new String[] {"*"},
                         "sms.db",
                         "sms_backup",
                         create_table,
                         expected_keys,
                         true);


        /*
        String storage = get_selected_storage();
        if (storage == null)
        {
            ui_log_text.append("\nStorage Not Selected!");
            return;
        }

        File dir = new File(storage, "mm/databases/");
        try
        {
            dir.mkdirs();
        }
        catch (Exception e)
        {
            ui_log_text.append("\nCan't Create Directory!" +
                                         e.getMessage());
            e.printStackTrace();
            return;
        }

        File file = new File(dir, "sms.db");

        Uri uri                  = Uri.parse("content://sms");
        String[] proj            = {"*"};
        ContentResolver resolver = getContentResolver();

        ui_log_text.append("\nListing SMS");

        ArrayList<Map<String, String>> rows = new ArrayList<>();

        try (Cursor cursor = resolver.query(uri, proj, null, null, null))
        {
            while (cursor.moveToNext())
            {
                HashMap<String, String> row = new HashMap<>();
                final String[] columns      = cursor.getColumnNames();

                for (String column : columns)
                {
                    String value = null;

                    try
                    {
                        final int index = cursor.getColumnIndex(column);
                        if (index == -1)
                            continue;
                        final int type = cursor.getType(index);

                        // if (type == Cursor.FIELD_TYPE_NULL)
                        //     value = null;
                        // else
                        if (type == Cursor.FIELD_TYPE_INTEGER)
                            value = cursor.getString(index);
                        else if (type == Cursor.FIELD_TYPE_FLOAT)
                            value = cursor.getString(index);
                        else if (type == Cursor.FIELD_TYPE_STRING)
                            value = cursor.getString(index);
                        else if (type == Cursor.FIELD_TYPE_BLOB)
                        {
                            byte[] bytes = cursor.getBlob(index);
                            StringBuilder builder =
                                new StringBuilder(bytes.length * 2);
                            for (byte b : bytes)
                                builder.append(String.format("%02x", b));
                            value = builder.toString();
                        }
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                    }

                    row.put(column, value);
                }

                rows.add(row);
            }
        }
        catch (Exception e)
        {
            ui_log_text.append("\nFailed to read sms content!\n" +
                                         e.getMessage());
            e.printStackTrace();
            return;
        }


        SQLiteDatabase db;
        try
        {
            db = SQLiteDatabase.openDatabase(
                file.toString(),
                null,
                SQLiteDatabase.OPEN_READWRITE |
                    SQLiteDatabase.CREATE_IF_NECESSARY);
        }
        catch (Exception e)
        {
            ui_log_text.append("\nFailed to open backup database! \n" +
                                         e.getMessage());
            e.printStackTrace();
            return;
        }

        String create_table = String.join(
            "",
            "CREATE TABLE IF NOT EXISTS sms_backup (",
            "[date] TEXT NOT NULL,",
            "[address] TEXT NOT NULL,",
            "[body] TEXT NOT NULL,",
            "[thread_id] TEXT,",
            "[date_sent] TEXT,",
            "[type] TEXT,",
            "[creator] TEXT,",
            "[read] TEXT,",
            "[subject] TEXT,",
            "[sub_id] TEXT,",
            "[reply_path_present] TEXT,",
            "[seen] TEXT,",
            "[protocol] TEXT,",
            "[person] TEXT,",
            "[service_center] TEXT,",
            "[error_code] TEXT,",
            "[_id] TEXT,",
            "[locked] TEXT,",
            "[status] TEXT,",
            "[full_object] TEXT NOT NULL,",
            "[import_created] TEXT NOT NULL",
            " DEFAULT (strftime('%Y-%m-%dT%H:%M:%S+00:00', 'now')),",
            "[import_modified] TEXT NOT NULL",
            " DEFAULT (strftime('%Y-%m-%dT%H:%M:%S+00:00', 'now')),",
            "UNIQUE([date], [address], [body], [thread_id], [type])",
            ")");

        try
        {
            db.execSQL(create_table);
        }
        catch (Exception e)
        {
            ui_log_text.append("\nFailed to create database table!\n" +
                                         e.getMessage());
            return;
        }


        StringBuilder all_sms_obj = new StringBuilder();

        List<String> expected_keys = Arrays.asList("date",
                                                   "address",
                                                   "body",
                                                   "thread_id",
                                                   "date_sent",
                                                   "type",
                                                   "creator",
                                                   "read",
                                                   "subject",
                                                   "sub_id",
                                                   "reply_path_present",
                                                   "seen",
                                                   "protocol",
                                                   "person",
                                                   "service_center",
                                                   "error_code",
                                                   "_id",
                                                   "locked",
                                                   "status");

        int _count = 0;

        for (Map<String, String> row : rows)
        {
            StringBuilder obj = new StringBuilder();

            StringBuilder     cols   = new StringBuilder();
            StringBuilder     params = new StringBuilder();
            ArrayList<String> vals   = new ArrayList<>();

            for (Map.Entry<String, String> entry : row.entrySet())
            {
                String key   = entry.getKey();
                String value = entry.getValue();

                // parsers should use \n\n[[key]]:\n as separator
                // hopefully no message body contains this
                obj.append("\n\n[[").append(key).append("]]:\n").append(value);

                if (expected_keys.contains(key))
                {
                    if (cols.length() > 0)
                    {
                        cols.append(", ");
                        params.append(", ");
                    }
                    cols.append(key);
                    params.append(":").append(key);
                    vals.add(value);
                }

                all_sms_obj.append(key).append(": ").append(value).append(", ");
            }

            all_sms_obj.append("\n\n");

            if (cols.length() > 0)
            {
                cols.append(", ");
                params.append(", ");
            }
            cols.append("full_object");
            params.append(":").append("full_object");
            vals.add(obj.toString());

            StringBuilder sql = new StringBuilder();
            sql.append("INSERT OR IGNORE INTO sms_backup (")
                .append(cols.toString())
                .append(") VALUES (")
                .append(params.toString())
                .append(");");

            try
            {
                db.execSQL(sql.toString(), vals.toArray());
            }
            catch (Exception e)
            {
                ui_log_text.append("\nFailed to insert into database!\n" +
                                             e.getMessage());
                e.printStackTrace();
            }

            _count += 1;
            if (_count % 5 == 0)
                ui_log_text.append("\nProcessed " + _count + " SMS");
        }

        ui_log_text.append("\nProcessed " + _count + " SMS");

        // ui_backup_sms_status.setText(ui_backup_sms_status.getText() + "\n\n"
        // + all_sms_obj.toString());
        // ui_backup_sms_status.setTextIsSelectable(true);

        if (db.isOpen())
            db.close();

        SimpleDateFormat date =
            new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.ENGLISH);
        // + date.format(new Date())
        File txt = new File(dir, "sms"  + ".txt");

        try (FileWriter writer = new FileWriter(txt))
        {
            writer.write(all_sms_obj.toString());
            writer.flush();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        */
    }




    void backup_call_logs()
    {
        String create_table = String.join(
            "",
            "CREATE TABLE IF NOT EXISTS calllog_backup (",
            "[date] TEXT NOT NULL,",
            "[number] TEXT NOT NULL,",
            "[type] TEXT NOT NULL,",
            "[duration] TEXT,",
            "[phone_account_hidden] TEXT,",
            "[photo_id] TEXT,",
            "[transcription] TEXT,",
            "[subscription_component_name] TEXT,",
            "[call_screening_app_name] TEXT,",
            "[geocoded_location] TEXT,",
            "[subscription_id] TEXT,",
            "[presentation] TEXT,",
            "[is_read] TEXT,",
            "[features] TEXT,",
            "[voicemail_uri] TEXT,",
            "[normalized_number] TEXT,",
            "[via_number] TEXT,",
            "[matched_number] TEXT,",
            "[last_modified] TEXT,",
            "[new] TEXT,",
            "[numberlabel] TEXT,",
            "[lookup_uri] TEXT,",
            "[photo_uri] TEXT,",
            "[formatted_number] TEXT,",
            "[block_reason] TEXT,",
            "[add_for_all_users] TEXT,",
            "[phone_account_address] TEXT,",
            "[data_usage] TEXT,",
            "[numbertype] TEXT,",
            "[call_screening_component_name] TEXT,",
            "[countryiso] TEXT,",
            "[name] TEXT,",
            "[post_dial_digits] TEXT,",
            "[transcription_state] TEXT,",
            "[_id] TEXT,",
            "[full_object] TEXT NOT NULL,",
            "[import_created] TEXT NOT NULL",
            " DEFAULT (strftime('%Y-%m-%dT%H:%M:%S+00:00', 'now')),",
            "[import_modified] TEXT NOT NULL",
            " DEFAULT (strftime('%Y-%m-%dT%H:%M:%S+00:00', 'now')),",
            "UNIQUE([date], [number], [type], [duration])",
            ")");

        List<String> expected_keys =
            Arrays.asList("date",
                          "number",
                          "type",
                          "duration",
                          "phone_account_hidden",
                          "photo_id",
                          "transcription",
                          "subscription_component_name",
                          "call_screening_app_name",
                          "geocoded_location",
                          "subscription_id",
                          "presentation",
                          "is_read",
                          "features",
                          "voicemail_uri",
                          "normalized_number",
                          "via_number",
                          "matched_number",
                          "last_modified",
                          "new",
                          "numberlabel",
                          "lookup_uri",
                          "photo_uri",
                          "formatted_number",
                          "block_reason",
                          "add_for_all_users",
                          "phone_account_address",
                          "data_usage",
                          "numbertype",
                          "call_screening_component_name",
                          "countryiso",
                          "name",
                          "post_dial_digits",
                          "transcription_state",
                          "_id");

        backup_telephony(CallLog.Calls.CONTENT_URI,
                         null,
                         "calllog.db",
                         "calllog_backup",
                         create_table,
                         expected_keys,
                         true);




        /*
        File dir = get_writeable_database_dir();
        if (dir == null)
        {
            ui_log_text.append("\nNo writeable dir found.");
            return;
        }

        File file = new File(dir, "calllog.db");

        Uri uri                  = CallLog.Calls.CONTENT_URI;
        String[] proj            = null; // {"*"};

        ui_log_text.append("\nListing call logs.");
        List<Map<String, String>> rows = content_uri_to_list(uri, proj);
        if (rows == null)
        {
            ui_log_text.append("\nFailed to list call logs.");
            return;
        }


        SQLiteDatabase db;
        try
        {
            db = SQLiteDatabase.openDatabase(
                file.toString(),
                null,
                SQLiteDatabase.OPEN_READWRITE |
                    SQLiteDatabase.CREATE_IF_NECESSARY);
        }
        catch (Exception e)
        {
            ui_log_text.append("\nFailed to open backup database! \n" +
                                         e.getMessage());
            e.printStackTrace();
            return;
        }

        String create_table = String.join(
            "",
            "CREATE TABLE IF NOT EXISTS calllog_backup (",
            "[date] TEXT NOT NULL,",
            "[number] TEXT NOT NULL,",
            "[type] TEXT NOT NULL,",
            "[duration] TEXT,",
            "[phone_account_hidden] TEXT,",
            "[photo_id] TEXT,",
            "[transcription] TEXT,",
            "[subscription_component_name] TEXT,",
            "[call_screening_app_name] TEXT,",
            "[geocoded_location] TEXT,",
            "[subscription_id] TEXT,",
            "[presentation] TEXT,",
            "[is_read] TEXT,",
            "[features] TEXT,",
            "[voicemail_uri] TEXT,",
            "[normalized_number] TEXT,",
            "[via_number] TEXT,",
            "[matched_number] TEXT,",
            "[last_modified] TEXT,",
            "[new] TEXT,",
            "[numberlabel] TEXT,",
            "[lookup_uri] TEXT,",
            "[photo_uri] TEXT,",
            "[formatted_number] TEXT,",
            "[block_reason] TEXT,",
            "[add_for_all_users] TEXT,",
            "[phone_account_address] TEXT,",
            "[data_usage] TEXT,",
            "[numbertype] TEXT,",
            "[call_screening_component_name] TEXT,",
            "[countryiso] TEXT,",
            "[name] TEXT,",
            "[post_dial_digits] TEXT,",
            "[transcription_state] TEXT,",
            "[_id] TEXT,",
            "[full_object] TEXT NOT NULL,",
            "[import_created] TEXT NOT NULL DEFAULT
        (strftime('%Y-%m-%dT%H:%M:%S+00:00', 'now')),",
            "[import_modified] TEXT NOT NULL DEFAULT
        (strftime('%Y-%m-%dT%H:%M:%S+00:00', 'now')),", "UNIQUE([date],
        [number], [type], [duration])",
            ")");

        try
        {
            db.execSQL(create_table);
        }
        catch (Exception e)
        {
            ui_log_text.append("\nFailed to create database table!\n" +
                                         e.getMessage());
            return;
        }

        List<String> expected_keys = Arrays.asList(
            "date",
            "number",
            "type",
            "duration",
            "phone_account_hidden",
            "photo_id",
            "transcription",
            "subscription_component_name",
            "call_screening_app_name",
            "geocoded_location",
            "subscription_id",
            "presentation",
            "is_read",
            "features",
            "voicemail_uri",
            "normalized_number",
            "via_number",
            "matched_number",
            "last_modified",
            "new",
            "numberlabel",
            "lookup_uri",
            "photo_uri",
            "formatted_number",
            "block_reason",
            "add_for_all_users",
            "phone_account_address",
            "data_usage",
            "numbertype",
            "call_screening_component_name",
            "countryiso",
            "name",
            "post_dial_digits",
            "transcription_state",
            "_id");


        int _count = 0;
        StringBuilder all_obj = new StringBuilder();
        */
    }
}


/*
date: 1661578168068, creator: com.android.messaging, address: 9846, date_sent:
1661578167000, read: 1, subject: null, sub_id: 1, reply_path_present: 0, type:
1, body: , seen: 1, thread_id: 6, protocol: 0, person: null, service_center:
null, error_code: -1, _id: 9, locked: 0, status: -1,

date: 1661578149491, creator: com.android.messaging, address: 456, date_sent:
1661578149000, read: 1, subject: null, sub_id: 1, reply_path_present: 0, type:
1, body: 襁, seen: 1, thread_id: 5, protocol: 0, person: null, service_center:
null, error_code: -1, _id: 8, locked: 0, status: -1,

date: 1661562061335, creator: com.android.messaging, address: 3, date_sent:
1661562062000, read: 1, subject: null, sub_id: 1, reply_path_present: 0, type:
1, body: ji, seen: 1, thread_id: 4, protocol: 0, person: null, service_center:
null, error_code: -1, _id: 7, locked: 0, status: -1,

date: 1661562053252, creator: com.android.messaging, address: 2, date_sent:
1661562053000, read: 1, subject: null, sub_id: 1, reply_path_present: 0, type:
1, body: helo, seen: 1, thread_id: 3, protocol: 0, person: null, service_center:
null, error_code: -1, _id: 6, locked: 0, status: -1,

date: 1661562046414, creator: com.android.messaging, address: 1, date_sent:
1661562047000, read: 1, subject: null, sub_id: 1, reply_path_present: 0, type:
1, body: hi, seen: 1, thread_id: 2, protocol: 0, person: null, service_center:
null, error_code: -1, _id: 5, locked: 0, status: -1,

date: 1661562039067, creator: com.android.messaging, address: 123456789,
date_sent: 1661562039000, read: 1, subject: null, sub_id: 1, reply_path_present:
0, type: 1, body: hi[D[D, seen: 1, thread_id: 1, protocol: 0, person: null,
service_center: null, error_code: -1, _id: 4, locked: 0, status: -1,

date: 1661562014735, creator: com.android.messaging, address: 123456789,
date_sent: 1661562015000, read: 1, subject: null, sub_id: 1, reply_path_present:
0, type: 1, body: hello, seen: 1, thread_id: 1, protocol: 0, person: null,
service_center: null, error_code: -1, _id: 3, locked: 0, status: -1,

date: 1661561913980, creator: com.android.messaging, address: 123456789,
date_sent: 1661561914000, read: 1, subject: null, sub_id: 1, reply_path_present:
0, type: 1, body: hi, seen: 1, thread_id: 1, protocol: 0, person: null,
service_center: null, error_code: -1, _id: 2, locked: 0, status: -1,

date: 1661561874267, creator: com.android.messaging, address: 123456789,
date_sent: 1661561874000, read: 1, subject: null, sub_id: 1, reply_path_present:
0, type: 1, body: hi, seen: 1, thread_id: 1, protocol: 0, person: null,
service_center: null, error_code: -1, _id: 1, locked: 0, status: -1,


CREATE TABLE sms_backup
(
    [date] TEXT NOT NULL,
    [address] TEXT NOT NULL,
    [body] TEXT NOT NULL,
    [thread_id] TEXT,
    [creator] TEXT,
    [date_sent] TEXT,
    [read] TEXT,
    [subject] TEXT,
    [sub_id] TEXT,
    [reply_path_present] TEXT,
    [type] TEXT,
    [seen] TEXT,
    [protocol] TEXT,
    [person] TEXT,
    [service_center] TEXT,
    [error_code] TEXT,
    [_id] TEXT,
    [locked] TEXT,
    [status] TEXT,
    [full_object] TEXT NOT NULL,
    UNIQUE([date], [address], [body])
)

*/




/*
CREATE TABLE IF NOT EXISTS calllog_backup (
    [date] TEXT NOT NULL,
    [number] TEXT NOT NULL,
    [type] TEXT NOT NULL,
    [duration] TEXT,
    [phone_account_hidden] TEXT,
    [photo_id] TEXT,
    [transcription] TEXT,
    [subscription_component_name] TEXT,
    [call_screening_app_name] TEXT,
    [geocoded_location] TEXT,
    [subscription_id] TEXT,
    [presentation] TEXT,
    [is_read] TEXT,
    [features] TEXT,
    [voicemail_uri] TEXT,
    [normalized_number] TEXT,
    [via_number] TEXT,
    [matched_number] TEXT,
    [last_modified] TEXT,
    [new] TEXT,
    [numberlabel] TEXT,
    [lookup_uri] TEXT,
    [photo_uri] TEXT,
    [formatted_number] TEXT,
    [block_reason] TEXT,
    [add_for_all_users] TEXT,
    [phone_account_address] TEXT,
    [data_usage] TEXT,
    [numbertype] TEXT,
    [call_screening_component_name] TEXT,
    [countryiso] TEXT,
    [name] TEXT,
    [post_dial_digits] TEXT,
    [transcription_state] TEXT,
    [_id] TEXT,
    [full_object] TEXT NOT NULL,
    [import_created] TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%S+00:00',
'now')), [import_modified] TEXT NOT NULL DEFAULT
(strftime('%Y-%m-%dT%H:%M:%S+00:00', 'now')), UNIQUE([date], [number], [type],
[duration])
)







[Log Text]
Listing call logs.

date: 1662873300668,
phone_account_hidden: 0,
photo_id: 0,
transcription: null,
subscription_component_name:
com.android.phone/com.android.services.telephony.TelephonyConnectionService,
call_screening_app_name: null,
type: 2,
geocoded_location: ,
duration: 5,
subscription_id: 89014103211118510720,
presentation: 1,
is_read: null,
number: 5488857,
features: 0,
voicemail_uri: null,
normalized_number: null,
via_number: ,
matched_number: null,
last_modified: 1662873308742,
new: 1,
numberlabel: null,
lookup_uri:
content://com.android.contacts/contacts/lookup/encoded?directory=9223372036854775807#{"display_name":"548-8857","display_name_source":20,"vnd.android.cursor.item\/contact":{"vnd.android.cursor.item\/phone_v2":{"data1":"548-8857","data2":0}}},
photo_uri: null,
formatted_number: 548-8857,
block_reason: 0,
add_for_all_users: 1,
phone_account_address: +15555215554,
data_usage: null,
numbertype: null,
call_screening_component_name: null,
countryiso: US,
name: null,
post_dial_digits: ,
transcription_state: 0,
_id: 1,

date: 1662873337718,
phone_account_hidden: 0,
photo_id: 0,
transcription: null,
subscription_component_name:
com.android.phone/com.android.services.telephony.TelephonyConnectionService,
call_screening_app_name: null,
type: 2,
geocoded_location: ,
duration: 1,
subscription_id: 89014103211118510720,
presentation: 1,
is_read: null,
number: 65188,
features: 0,
voicemail_uri: null,
normalized_number: null,
via_number: ,
matched_number: null,
last_modified: 1662873341638,
new: 1,
numberlabel: null,
lookup_uri:
content://com.android.contacts/contacts/lookup/encoded?directory=9223372036854775807#{"display_name":"65188","display_name_source":20,"vnd.android.cursor.item\/contact":{"vnd.android.cursor.item\/phone_v2":{"data1":"65188","data2":0}}},
photo_uri: null,
formatted_number: 65188,
block_reason: 0,
add_for_all_users: 1,
phone_account_address: +15555215554,
data_usage: null,
numbertype: null,
call_screening_component_name: null,
countryiso: US,
name: null,
post_dial_digits: ,
transcription_state: 0,
_id: 2,
*/
