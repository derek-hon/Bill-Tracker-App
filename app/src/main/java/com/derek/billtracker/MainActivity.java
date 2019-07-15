package com.derek.billtracker;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.googlecode.tesseract.android.TessBaseAPI;
import com.theartofdev.edmodo.cropper.CropImage;
import com.theartofdev.edmodo.cropper.CropImageView;

import net.sqlcipher.database.SQLiteDatabase;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    EditText ocrYearText, ocrMonthText, ocrDayText, ocrAmountText;
    ImageView previewImageView;
    FloatingActionButton cameraFAB;
    ScrollView resultsView;
    LinearLayout scannerLayout, receiptLayout, expenseLayout;
    RadioButton scannerButton, receiptsButton, expensesButton;
    Button saveButton;
    Spinner expenseDropdown;

    private static final int CAMERA_REQUEST_CODE = 200;
    private static final int STORAGE_REQUEST_CODE = 400;
    private static final int IMAGE_PICK_GALLERY_CODE = 1000;
    private static final int IMAGE_PICK_CAMERA_CODE = 1001;

    String imageFilePath;

    String[] cameraPermissions;
    String[] storagePermissions;

    Uri imageURI;

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SQLiteDatabase.loadLibs(this);

        copyTessDataForTextRecognizor();

        ActionBar actionBar = getSupportActionBar();
        Objects.requireNonNull(actionBar).setSubtitle("Click the image button to insert an image");

        ocrYearText = findViewById(R.id.receiptYear);
        ocrMonthText = findViewById(R.id.receiptMonth);
        ocrDayText = findViewById(R.id.receiptDay);
        ocrAmountText = findViewById(R.id.receiptAmount);

        previewImageView = findViewById(R.id.imagePreview);
        resultsView = findViewById(R.id.resultView);

        scannerLayout = findViewById(R.id.scannerLayout);
        receiptLayout = findViewById(R.id.receiptLayout);
        expenseLayout = findViewById(R.id.expenseLayout);

        cameraFAB = findViewById(R.id.fab);
        saveButton = findViewById(R.id.saveOCR);
        scannerButton = findViewById(R.id.scanner);
        receiptsButton = findViewById(R.id.receipts);
        expensesButton = findViewById(R.id.expenses);

        expenseDropdown = findViewById(R.id.expenseDropdown);
        setExpenseDropdown();

        cameraPermissions = new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };
        storagePermissions = new String[]{
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
        };

        cameraFAB.setOnClickListener(this);
        saveButton.setOnClickListener(this);
        scannerButton.setOnClickListener(this);
        receiptsButton.setOnClickListener(this);
        expensesButton.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.fab:
                showImageImportDialog();
                break;
            case R.id.saveOCR:
                if (ocrAmountText.getText().toString().matches("")) {
                    Toast.makeText(this, "Please enter the receipt amount that we failed to retrieve", Toast.LENGTH_SHORT).show();
                    break;
                } else if (ocrYearText.getText().toString().matches("")) {
                    Toast.makeText(this, "Please enter the receipt date that we failed to retrieve", Toast.LENGTH_SHORT).show();
                    break;
                }
                insertData(saveInfo());
                break;
            case R.id.scanner:
                setButtonBackground(R.id.scanner);
                setLayoutVisibility(R.id.scannerLayout, false);
                break;
            case R.id.receipts:
                setButtonBackground(R.id.receipts);
//                scannerLayout.setVisibility(View.GONE);
//                resultsView.setVisibility(View.GONE);
                setReceiptData();
                setLayoutVisibility(R.id.receiptLayout, false);
                break;
            case R.id.expenses:
                setButtonBackground(R.id.expenses);
                setExpenseData(0);
                setLayoutVisibility(R.id.expenseLayout, false);
                break;
            default:
                break;
        }
    }

    void setExpenseDropdown() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String[] items = new String[] { "Current Month", "Year To Day" };
                ArrayAdapter<String> adapter = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_spinner_dropdown_item, items);
                expenseDropdown.setAdapter(adapter);
                expenseDropdown.setSelection(0);
            }
        });
    }

    private void insertData(ContentValues contentValues) {
        SQLiteDatabase db = DataHelper.getInstance(this).getWritableDatabase("somePassword123");

        db.insertWithOnConflict(DataHelper.DB_TABLE, null, contentValues, SQLiteDatabase.CONFLICT_IGNORE);
        db.close();

        Toast.makeText(this, "Successfully saved information", Toast.LENGTH_SHORT).show();

        setLayoutVisibility(R.id.scannerLayout, false);
    }

    private ContentValues saveInfo() {
        ContentValues contentValues = new ContentValues();

        contentValues.put(DataHelper.MONTH, ocrMonthText.getText().toString());
        contentValues.put(DataHelper.DAY, ocrDayText.getText().toString());
        contentValues.put(DataHelper.YEAR, ocrYearText.getText().toString());
        contentValues.put(DataHelper.AMOUNT, ocrAmountText.getText().toString());
        contentValues.put(DataHelper.PATH, imageFilePath);

        return contentValues;
    }

    private void copyTessDataForTextRecognizor() {
        Runnable run = new Runnable() {
            @Override
            public void run() {
                AssetManager assetManager = MainActivity.this.getAssets();
                OutputStream out = null;
                try {
                    InputStream in = assetManager.open("eng.traineddata");
                    String tessPath = tessDataPath();
                    File tessFolder = new File(tessPath);
                    if (!tessFolder.exists())
                        tessFolder.mkdir();
                    String tessData = tessPath + "/" + "eng.traineddata";
                    File tessFile = new File(tessData);
                    if (!tessFile.exists()) {
                        out = new FileOutputStream(tessData);
                        byte[] buffer = new byte[1024];
                        int read = in.read(buffer);
                        while (read != -1) {
                            out.write(buffer, 0, read);
                            read = in.read(buffer);
                        }
                        Log.d("MainActivity", " Finished copying file");
                    } else
                        Log.d("MainActivity", " Tess file already exists");

                } catch (Exception e) {
                    Log.d("MainApplication", "couldn't copy with the following error : " + e.toString());
                } finally {
                    try {
                        if (out != null)
                            out.close();
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, e.toString(), Toast.LENGTH_SHORT).show();
                    }
                }
            }
        };
        new Thread(run).start();
    }

    private String tessDataPath() {
        return MainActivity.this.getExternalFilesDir(null) + "/tessdata/";
    }

    private String getTessDataParentDirectory() {
        return Objects.requireNonNull(getApplicationContext().getExternalFilesDir(null)).getPath();
    }

    private void showImageImportDialog() {
        String[] items = {"Camera", "Gallery"};
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle("Select Image");
        dialog.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    if (!checkCameraPermission()) {
                        requestCameraPermission();
                    } else {
                        pickCamera();
                    }
                }
                if (which == 1) {
                    if (!checkStoragePermission()) {
                        requestStoragePermission();
                    } else {
                        pickGallery();
                    }
                }
            }
        });
        dialog.create().show();
    }

    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA) == (PackageManager.PERMISSION_GRANTED)
                &&
                ContextCompat.checkSelfPermission(this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE) == (PackageManager.PERMISSION_GRANTED);
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this, cameraPermissions, CAMERA_REQUEST_CODE);
    }

    private boolean checkStoragePermission() {
        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == (PackageManager.PERMISSION_GRANTED);
    }

    private void requestStoragePermission() {
        System.out.println("requesting storage permission");
        ActivityCompat.requestPermissions(this, storagePermissions, STORAGE_REQUEST_CODE);
    }

    private void pickCamera() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "NewPicture");
        values.put(MediaStore.Images.Media.DESCRIPTION, "Image To Text OCR");
        imageURI = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageURI);
        startActivityForResult(cameraIntent, IMAGE_PICK_CAMERA_CODE);
    }

    private void pickGallery() {
        Intent galleryIntent = new Intent(Intent.ACTION_PICK);
        galleryIntent.setType("image/*");
        startActivityForResult(galleryIntent, IMAGE_PICK_GALLERY_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case CAMERA_REQUEST_CODE:
                if (grantResults.length > 0) {
                    System.out.println(Arrays.toString(grantResults));
                    boolean cameraAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                    boolean writeStorageAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;

                    if (cameraAccepted && writeStorageAccepted)
                        pickCamera();
                    else
                        Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
                }
                break;
            case STORAGE_REQUEST_CODE:
                if (grantResults.length > 0) {
                    boolean writeStorageAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;

                    if (writeStorageAccepted)
                        pickGallery();
                    else
                        Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode != RESULT_CANCELED) {
            if (resultCode == RESULT_OK) {
                if (requestCode == IMAGE_PICK_GALLERY_CODE) {
                    CropImage.activity(Objects.requireNonNull(data).getData())
                            .setGuidelines(CropImageView.Guidelines.ON)
                            .start(this);

                    Uri selectedImage = data.getData();
                    imageFilePath = getSelectedImagePath(selectedImage);
                }
                if (requestCode == IMAGE_PICK_CAMERA_CODE) {
                    CropImage.activity(imageURI)
                            .setGuidelines(CropImageView.Guidelines.ON)
                            .start(this);

                    assert data != null;
                    Bitmap receiptImage = (Bitmap) Objects.requireNonNull(data.getExtras()).get("data");
                    Uri tempUri = getImageURI(getApplicationContext(), Objects.requireNonNull(receiptImage));
                    imageFilePath = getRealPathFromUri(tempUri);
                }
            }

            if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
                CropImage.ActivityResult result = CropImage.getActivityResult(data);

                if (resultCode == RESULT_OK) {
                    Uri resultURI = Objects.requireNonNull(result).getUri();

                    setImageView(resultURI);

                    BitmapDrawable bitmapDrawable = (BitmapDrawable) previewImageView.getDrawable();
                    Bitmap bitmap = bitmapDrawable.getBitmap();
                    try {
                        new AsyncTessAPITask().execute(bitmap).get();
                    } catch (ExecutionException e) {
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } else if (resultCode == CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE) {
                    Exception error = Objects.requireNonNull(result).getError();
                    Toast.makeText(this, "" + error, Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private String getSelectedImagePath(Uri imageURI) {
        String path = "";
        String[] projection = {MediaStore.Images.Media.DATA};
        if (getContentResolver() != null) {
            Cursor cursor = getContentResolver().query(imageURI, projection, null, null, null);
            if (cursor != null) {
                cursor.moveToFirst();
                int index = cursor.getColumnIndexOrThrow(projection[0]);
                path = cursor.getString(index);
                cursor.close();
            }
        }
        if (path == null)
            path = "Not found";
        return path;
    }

    private Uri getImageURI(Context context, Bitmap image) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        image.compress(Bitmap.CompressFormat.JPEG, 100, bytes);

        String path = MediaStore.Images.Media.insertImage(context.getContentResolver(), image, "Title", null);
        return Uri.parse(path);
    }

    private String getRealPathFromUri(Uri imageURI) {
        String path = "";

        if (getContentResolver() != null) {
            Cursor cursor = getContentResolver().query(imageURI, null, null, null, null);
            if (cursor != null) {
                cursor.moveToFirst();
                int index = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
                path = cursor.getString(index);
                cursor.close();
            }
        }
        if (path == null)
            path = "Not found";
        return path;
    }

    static double round(double value) {
        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(2, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    void setReceiptData() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                int month = Calendar.getInstance().get(Calendar.MONTH) + 1;
                SQLiteDatabase db = DataHelper.getInstance(MainActivity.this).getReadableDatabase("somePassword123");
                String[] column = new String[]{DataHelper.DAY, DataHelper.MONTH, DataHelper.YEAR, DataHelper.PATH};

                receiptLayout.removeAllViews();

                for (int i = month; i > 0; i--) {
                    TextView currentMonth = new TextView(MainActivity.this);
                    currentMonth.setText(getMonthText(i));
                    currentMonth.setTextSize(TypedValue.COMPLEX_UNIT_SP, 23f);
                    currentMonth.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                    currentMonth.setBackgroundColor(Color.parseColor("#FFD35E"));

                    receiptLayout.addView(currentMonth);

                    String[] monthSelection = new String[]{i + ""};
                    receiptLayout.addView(getImages(monthSelection, db, column));
                }

                db.close();
            }
        });
    }

    void setExpenseData(final int option) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                System.out.println("expense data");
                String[] args;
                String selection,
                       orderBy;

                int currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1,
                    daysInMonth;

                if (option == 0) {
                    args = new String[]{currentMonth + ""};
                    selection = DataHelper.MONTH + " = ?";
                    orderBy = DataHelper.DAY + " ASC";
                }
                else {
                    args = null;
                    selection = null;
                    orderBy = DataHelper.MONTH + " ASC";
                }

                System.out.println(selection);
                System.out.println(Arrays.toString(args));
                SQLiteDatabase db = DataHelper.getInstance(MainActivity.this).getReadableDatabase("somePassword123");
                String[] column = new String[]{DataHelper.DAY, DataHelper.MONTH, DataHelper.AMOUNT, DataHelper.YEAR};

                Cursor cursor = db.query(
                        DataHelper.DB_TABLE,
                        column,
                        selection,
                        args,
                        null,
                        null,
                        orderBy);

                if (cursor.getCount() > 0) {
                    System.out.println("?");
                    LinearLayout firstWeekLayout  = new LinearLayout(MainActivity.this),
                                 secondWeekLayout = new LinearLayout(MainActivity.this),
                                 thirdWeekLayout  = new LinearLayout(MainActivity.this),
                                 fourthWeekLayout = new LinearLayout(MainActivity.this),
                                 fifthWeekLayout  = new LinearLayout(MainActivity.this);

                    double[] weeks  = new double[5];
                    double[] months = new double [12];

                    cursor.moveToFirst();

                    while (!cursor.isAfterLast()) {
                        int day       = Integer.parseInt(cursor.getString(0)),
                            month     = Integer.parseInt(cursor.getString(1)),
                            year      = Integer.parseInt(cursor.getString(3));
                        double amount = Double.parseDouble(cursor.getString(2));


                        if (option == 1 && month != currentMonth)
                            months[month - 1] += amount;
                        else {
                            double[] weekAmount = separateIntoWeeks(day, amount);
                            weeks[(int)weekAmount[0]] += weekAmount[1];
                        } //end else

                        cursor.moveToNext();
                    } //end while
                    System.out.println("arrays");
                    System.out.println(Arrays.toString(weeks));
                    System.out.println(Arrays.toString(months));

                } //end if


                if (cursor != null && !cursor.isClosed())
                    cursor.close();
                db.close();
            }
        });
    }

    double[] separateIntoWeeks(int day, double amount) {
        if (day <= 7)
            return new double[] {0, amount};
        else if (day <= 14)
            return new double[] {1, amount};
        else if (day <= 21)
            return new double[] {2, amount};
        else if (day <= 28)
            return new double[] {3, amount};
        else
            return new double[] {4, amount};
    }

    String getMonthText(int monthNumber) {
        String monthText = "";
        switch (monthNumber) {
            case 1:
                monthText = "January";
                break;
            case 2:
                monthText = "February";
                break;
            case 3:
                monthText = "March";
                break;
            case 4:
                monthText = "April";
                break;
            case 5:
                monthText = "May";
                break;
            case 6:
                monthText = "June";
                break;
            case 7:
                monthText = "July";
                break;
            case 8:
                monthText = "August";
                break;
            case 9:
                monthText = "September";
                break;
            case 10:
                monthText = "October";
                break;
            case 11:
                monthText = "November";
                break;
            case 12:
                monthText = "December";
                break;
        }
        return monthText;
    }

    LinearLayout getImages(String[] month, SQLiteDatabase readDB, String[] columnArray) {
        LinearLayout imagesLayout = new LinearLayout(MainActivity.this);
        imagesLayout.setOrientation(LinearLayout.HORIZONTAL);
        imagesLayout.setPadding(10, 0, 10, 0);

        Cursor cursor = readDB.query(
                DataHelper.DB_TABLE,
                columnArray,
                DataHelper.MONTH + " = ?",
                month,
                null,
                null,
                DataHelper.DAY + " DESC");

        if (cursor.getCount() > 0) {
            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);

                LinearLayout imageLayout = new LinearLayout(MainActivity.this);
                imageLayout.setOrientation(LinearLayout.VERTICAL);
                imageLayout.setBackgroundColor(Color.parseColor("#FFEFC4"));
                imageLayout.setLayoutParams(params);
                imageLayout.setPadding(5, 0, 0, 5);

                String date = cursor.getString(0) + "/" + cursor.getString(1) + "/" + cursor.getString(2);
                Bitmap receiptThumbnail = ThumbnailUtils.extractThumbnail(BitmapFactory.decodeFile(cursor.getString(3)), 128, 128);

                ImageView receiptView = new ImageView(MainActivity.this);
                receiptView.setTag(cursor.getString(3));
                receiptView.setOnClickListener(viewReceiptDetails);

                receiptView.setImageBitmap(receiptThumbnail);
                receiptView.setPadding(10, 0, 10, 0);

                TextView dateView = new TextView(MainActivity.this);
                dateView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                dateView.setText(date);

                imageLayout.addView(receiptView);
                imageLayout.addView(dateView);
                imagesLayout.addView(imageLayout);

                cursor.moveToNext();
            } //end while
        } //end if

        if (cursor != null && !cursor.isClosed())
            cursor.close();

        return imagesLayout;
    }

    View.OnClickListener viewReceiptDetails = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            viewReceiptData(v.getTag().toString());
        }
    };

    void viewReceiptData(final String path) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                File receiptFile = new File(path);
                BitmapFactory.Options options = new BitmapFactory.Options();
                Bitmap bitmap = BitmapFactory.decodeFile(receiptFile.getAbsolutePath(), options);

                ReceiptDialogFragment receiptDialogFragment = new ReceiptDialogFragment();

                Bundle bundle = new Bundle();
                bundle.putParcelable("bitmap", bitmap);
                receiptDialogFragment.setArguments(bundle);

                receiptDialogFragment.show(getSupportFragmentManager(), "ReceiptDialog");
            }
        });
    }

    void setButtonBackground(final int buttonId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                switch (buttonId) {
                    case R.id.scanner:
                        receiptsButton.setBackgroundResource(0);
                        expensesButton.setBackgroundResource(0);
                        scannerButton.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.textlines, null));
                        break;
                    case R.id.receipts:
                        scannerButton.setBackgroundResource(0);
                        expensesButton.setBackgroundResource(0);
                        receiptsButton.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.textlines, null));
                        break;
                    case R.id.expenses:
                        scannerButton.setBackgroundResource(0);
                        expensesButton.setBackgroundResource(0);
                        expensesButton.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.textlines, null));
                        break;
                    default:
                        Toast.makeText(MainActivity.this, "Error occurred while clicking button", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    void setLayoutVisibility(final int layout, final boolean results) {
        final Semaphore mutex = new Semaphore(0);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                switch (layout) {
                    case R.id.scannerLayout:
                        checkResultView(results);
                        break;
                    case R.id.receiptLayout:
                        scannerLayout.setVisibility(View.GONE);
                        expenseLayout.setVisibility(View.GONE);
                        receiptLayout.setVisibility(View.VISIBLE);
                        break;
                    case R.id.expenseLayout:
                        scannerLayout.setVisibility(View.GONE);
                        receiptLayout.setVisibility(View.GONE);
                        expenseLayout.setVisibility(View.VISIBLE);
                        break;
                    default:
                        Toast.makeText(MainActivity.this, "Error setting different display", Toast.LENGTH_SHORT).show();
                }

                mutex.release();
            }
        });
        try {
            mutex.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    void checkResultView(boolean results) {
        if (results) {
            scannerLayout.setVisibility(View.GONE);
            resultsView.setVisibility(View.VISIBLE);
        } else {
            receiptLayout.setVisibility(View.GONE);
            expenseLayout.setVisibility(View.GONE);
            resultsView.setVisibility(View.GONE);
            scannerLayout.setVisibility(View.VISIBLE);
        }
    }

    protected void setOCRText(final String[] text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ocrMonthText.setText(text[0]);
                ocrDayText.setText(text[1]);
                ocrYearText.setText(text[2]);
                ocrAmountText.setText(text[3]);
            }
        });
    }

    private void setImageView(final Uri croppedImage) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                previewImageView.setImageURI(croppedImage);
            }
        });
    }

    private class AsyncTessAPITask extends AsyncTask<Bitmap, Integer, String> {

        private final String TAG = MainActivity.class.getSimpleName();
        private TessBaseAPI tessBaseAPI;

        @Override
        protected String doInBackground(Bitmap... bitmaps) {
            Bitmap imageBitmap = bitmaps[0];

            try {
                tessBaseAPI = new TessBaseAPI();
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            }

            tessBaseAPI.init(getTessDataParentDirectory(), "eng");
            tessBaseAPI.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO);
            tessBaseAPI.setImage(imageBitmap);
            String returnString = "No result";

            try {
                returnString = tessBaseAPI.getUTF8Text();
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            }
            tessBaseAPI.end();
            return returnString.replace("\\s", "");
        }

        //y-m-d m-d-y
        protected void onPostExecute(String result) {
            String[] setTextData = new String[4];

            Pattern datePattern = Pattern.compile("([0-9]{1,2}([/\\-~'— ])[0-9]{1,2}([/\\-~— '])[0-9]{4})");
            Matcher dateMatcher = datePattern.matcher(result);

            Pattern datePatternTwo = Pattern.compile("([0-9]{4}([/\\-~'— ])[0-9]{1,2}([/\\-~'— ])[0-9]{1,2})");
            Matcher dateMatcherTwo = datePatternTwo.matcher(result);

            Pattern amountPattern = Pattern.compile("(total( \\$[0-9]*.[0-9]{2}|((\\r\\n)|(\\n))\\$[0-9]*.[0-9]{2}))", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
            Matcher amountMatcher = amountPattern.matcher(result);

            if (dateMatcher.find()) {
                String dateResult = dateMatcher.group(0).replaceAll("/-~'—\\s", "-");
                String month = dateResult.substring(0, 1);
                String day = dateResult.substring(3, 4);
                String year = dateResult.substring(6, 9);
                setTextData[0] = month;
                setTextData[1] = day;
                setTextData[2] = year;
            }
            if (dateMatcherTwo.find()) {
                String dateResult = dateMatcherTwo.group(0).replaceAll("/-~'—\\s", "-");
                String year = dateResult.substring(0, 3);
                String month = dateResult.substring(5, 6);
                String day = dateResult.substring(8, 9);
                setTextData[0] = month;
                setTextData[1] = day;
                setTextData[2] = year;
            }

            if (amountMatcher.find()) {
                String amountResult = amountMatcher.group(0).trim().replaceAll("(?i)total ", "");
                amountResult = amountResult.replaceAll("[^\\d$]+", ".");
                setTextData[3] = amountResult;
            } else {
                Pattern tempAmount = Pattern.compile("(amount \\$[0-9]*\\D[0-9]{2})", Pattern.CASE_INSENSITIVE);
                Pattern tempTip = Pattern.compile("Tip \\$[0-9]*\\D[0-9]{2}", Pattern.CASE_INSENSITIVE);


                Matcher tempAmountMatcher = tempAmount.matcher(result);
                Matcher tempTipMatcher = tempTip.matcher(result);

                if (tempAmountMatcher.find() && tempTipMatcher.find()) {
                    String tempAmountValue = tempAmountMatcher.group(0).trim().replaceAll("(?i)amount ", "");
                    tempAmountValue = tempAmountValue.replaceAll("\\D", ".");
                    tempAmountValue = tempAmountValue.replaceFirst("\\D", "");

                    String tempTipValue = tempTipMatcher.group(0).trim().replaceAll("(?i)tip ", "");
                    tempTipValue = tempTipValue.replaceAll("\\D", ".");
                    tempTipValue = tempTipValue.replaceFirst("\\D", "");

                    double tempTotalAmount = MainActivity.round(Double.parseDouble(tempAmountValue) + Double.parseDouble(tempTipValue));
                    String tempTotalText = "$" + tempTotalAmount;
                    setTextData[3] = tempTotalText;
                }
            }
            setLayoutVisibility(R.id.scannerLayout, true);
            setOCRText(setTextData);
        }
    }
}
