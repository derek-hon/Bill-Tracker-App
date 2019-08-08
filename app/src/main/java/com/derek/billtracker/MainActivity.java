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
import android.graphics.drawable.ColorDrawable;
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
import android.text.Html;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.googlecode.tesseract.android.TessBaseAPI;
import com.theartofdev.edmodo.cropper.CropImage;
import com.theartofdev.edmodo.cropper.CropImageView;

import net.sqlcipher.database.SQLiteDatabase;

import org.junit.Assert;

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

/**
 *
 */
public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    EditText ocrYearText, ocrMonthText, ocrDayText, ocrAmountText;
    ImageView previewImageView;
    FloatingActionButton cameraFAB;
    ScrollView resultsView;
    LinearLayout scannerLayout, receiptLayout, expenseDataLayout;
    RelativeLayout expenseLayout;
    RadioButton scannerButton, receiptsButton, expensesButton;
    Button saveButton;
    Spinner expenseDropdown;

    private static final int CAMERA_REQUEST_CODE = 200;
    private static final int STORAGE_REQUEST_CODE = 400;
    private static final int IMAGE_PICK_GALLERY_CODE = 1000;
    private static final int IMAGE_PICK_CAMERA_CODE = 1001;

    private Context context;

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

        context = getApplicationContext();

        copyTessDataForTextRecognizor();

        ActionBar actionBar = getSupportActionBar();
        Objects.requireNonNull(actionBar).setSubtitle("");
        actionBar.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#b8d177")));
        actionBar.setTitle(Html.fromHtml("<font color='#000000'>Receipt Expense Tracker </font>"));

        ocrYearText   = findViewById(R.id.receiptYear);
        ocrMonthText  = findViewById(R.id.receiptMonth);
        ocrDayText    = findViewById(R.id.receiptDay);
        ocrAmountText = findViewById(R.id.receiptAmount);

        previewImageView = findViewById(R.id.imagePreview);
        resultsView      = findViewById(R.id.resultView);

        scannerLayout     = findViewById(R.id.scannerLayout);
        receiptLayout     = findViewById(R.id.receiptLayout);
        expenseLayout     = findViewById(R.id.expenseLayout);
        expenseDataLayout = findViewById(R.id.expenseData);

        cameraFAB           = findViewById(R.id.fab);
        saveButton          = findViewById(R.id.saveOCR);
        scannerButton       = findViewById(R.id.scanner);
        receiptsButton      = findViewById(R.id.receipts);
        expensesButton      = findViewById(R.id.expenses);

        expenseDropdown = findViewById(R.id.expenseDropdown);
        setExpenseDropdown();
        expenseDropdown.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0)
                    setExpenseData(0);
                else
                    setExpenseData(1);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

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
        } //end switch
    } //onClick

    //*******************************************Begin scanner*******************************************

    /**
     * Simple alert dialog that will get proper permissions for whichever choice is made. Camera permissions
     * then starting up camera if camera is picked, storage permissions and image gallery if gallery is picked.
     */
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
    } //showImageImportDialog

    /**
     * Checks the manifest for camera permission and context for storage access permission
     * @return permission status
     */
    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA) == (PackageManager.PERMISSION_GRANTED)
                &&
                ContextCompat.checkSelfPermission(this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE) == (PackageManager.PERMISSION_GRANTED);
    } //checkCameraPermission

    /**
     * Function to request camera permission.
     */
    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this, cameraPermissions, CAMERA_REQUEST_CODE);
    } //requestCameraPermission

    /**
     * Checks context for storage permission
     * @return permission status
     */
    private boolean checkStoragePermission() {
        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == (PackageManager.PERMISSION_GRANTED);
    } //checkStoragePermission

    /**
     * Function to request storage permission
     */
    private void requestStoragePermission() {
        ActivityCompat.requestPermissions(this, storagePermissions, STORAGE_REQUEST_CODE);
    } //requestStoragePermission

    /**
     * Sets information to the uri and appends it onto the camera intent before starting the camera
     * activity.
     */
    private void pickCamera() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "NewPicture");
        values.put(MediaStore.Images.Media.DESCRIPTION, "Image To Text OCR");
        imageURI = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageURI);
        startActivityForResult(cameraIntent, IMAGE_PICK_CAMERA_CODE);
    } //pickCamera

    /**
     * Specifies the folder to retrieve from for the intent before starting it.
     */
    private void pickGallery() {
        Intent galleryIntent = new Intent(Intent.ACTION_PICK);
        galleryIntent.setType("image/*");
        startActivityForResult(galleryIntent, IMAGE_PICK_GALLERY_CODE);
    } //pickGallery

    /**
     * Function is called after user chooses whether or not to allow the permissions for the choice
     * they made. If permissions are granted then the intents will be executed to scan the receipt
     * from gallery or from image taken.
     *
     * @param requestCode permission request code
     * @param permissions permissions being requested
     * @param grantResults choice made by user
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case CAMERA_REQUEST_CODE:
                if (grantResults.length > 0) {
                    boolean cameraAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;

                    if (cameraAccepted)
                        pickCamera();
                    else
                        Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
                } //end if
                break;
            case STORAGE_REQUEST_CODE:
                if (grantResults.length > 0) {
                    boolean writeStorageAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;

                    if (writeStorageAccepted)
                        pickGallery();
                    else
                        Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
                } //end if
                break;
            default:
                break;
        } //end switch
    } //onRequestPermissionResult

    /**
     * Makes sure that the request isn't cancelled and that the result is ok before checking which
     * request was made. If the request was choosing a gallery image or taking a picture then the
     * file path will be retrieved from this after the crop image activity is called. If the action
     * was to crop the image then the bitmap of the image resulting from the crop activity will
     * be sent to the asynchronous class responsible for scanning using OCR.
     *
     * @see AsyncTessAPITask
     *
     * @param requestCode request code
     * @param resultCode result from request
     * @param data intent data
     */
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
    } //onActivityResult

    /**
     * After the OCR is done this function will be called to set the text which may or may not have
     * been retrieved from the receipt and be put in the text fields.
     * @param text array of retrieved text
     */
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
    } //setOCRText

    /**
     * A preview of the image which was cropped will be displayed
     * @param croppedImage image uri
     */
    private void setImageView(final Uri croppedImage) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                previewImageView.setImageURI(croppedImage);
            }
        });
    } //setImageView

    //*******************************************End scanner*******************************************


    //*******************************************Begin helpers*******************************************
    void setExpenseDropdown() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String[] items = new String[] { "Current Month", "Year To Day" };
                ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, items);
                expenseDropdown.setAdapter(adapter);
                expenseDropdown.setSelection(0);
            }
        });
    } //setExpenseDropdown

    private void insertData(ContentValues contentValues) {
        SQLiteDatabase db = DataHelper.getInstance(this).getWritableDatabase("somePassword123");

        db.insertWithOnConflict(DataHelper.DB_TABLE, null, contentValues, SQLiteDatabase.CONFLICT_IGNORE);
        db.close();

        Toast.makeText(this, "Successfully saved information", Toast.LENGTH_SHORT).show();

        setLayoutVisibility(R.id.scannerLayout, false);
    } //insertData

    private ContentValues saveInfo() {
        ContentValues contentValues = new ContentValues();

        contentValues.put(DataHelper.MONTH, ocrMonthText.getText().toString());
        contentValues.put(DataHelper.DAY, ocrDayText.getText().toString());
        contentValues.put(DataHelper.YEAR, ocrYearText.getText().toString());
        contentValues.put(DataHelper.AMOUNT, ocrAmountText.getText().toString());
        contentValues.put(DataHelper.PATH, imageFilePath);

        return contentValues;
    } //saveInfo

    /**
     * Changes the button's background resource to nothing or the drawable
     *
     * @param buttonId id of the button
     */
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
                        receiptsButton.setBackgroundResource(0);
                        expensesButton.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.textlines, null));
                        break;
                    default:
                        Toast.makeText(context, "Error occurred while clicking button", Toast.LENGTH_SHORT).show();
                } //end switch
            }
        });
    } //setButtonBackground

    /**
     * Responsible for switching the display depending on which tab was selected.
     *
     * @param layout resource id for the layout
     * @param results to check if the scanner was just used
     */
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
                        Toast.makeText(context, "Error setting different display", Toast.LENGTH_SHORT).show();
                }

                mutex.release();
            }
        });
        try {
            mutex.acquire();
        } //end try
        catch (InterruptedException e) {
            e.printStackTrace();
        } //end catch
    } //setLayoutVisibility

    /**
     * Changes to the scanner tab or results of the scan
     *
     * @param results determines whether scanner was used
     */
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
    } //checkResultView

    /**
     * Retrieves a uri from a parsed path which is retrieved from inserting an image bitmap into the context's
     * content.
     *
     * @param context context
     * @param image image bitmap
     * @return uri
     */
    private Uri getImageURI(Context context, Bitmap image) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        image.compress(Bitmap.CompressFormat.JPEG, 100, bytes);

        String path = MediaStore.Images.Media.insertImage(context.getContentResolver(), image, "Title", null);
        return Uri.parse(path);
    }

    /**
     * Similar to the function obtaining the real path, but instead ensures that the data from the
     * image media is in the columns that are returned by the query. This same column is
     * then retrieved as the image path.
     *
     * @param imageURI images uri
     * @return string path
     */
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
    } //getSelectedImagePath

    /**
     * Makes a query to the data storage using the images URI(uniform resource identifier). Once
     * the query is completed we obtain the column index for the image and retrieve the string path
     * which is in the column of the image column's data.
     *
     * @param imageURI uri
     * @return string path
     */
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
    } //getRealPathFromUri

    /**
     * Just a helper function that rounds values according to big decimal
     *
     * @param value value to be rounded
     * @return double value that is rounded
     */
    static double round(double value) {
        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(2, RoundingMode.HALF_UP);
        return bd.doubleValue();
    } //round

    /**
     * Abstracted out long switch case for determining month name from the number value. This is for
     * the expense tab for year to day as well as receipt tab for categorizing receipts
     *
     * @param monthNumber month in number format
     * @return string name of month
     */
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
    } //getMonthText


    //*******************************************end helpers*******************************************






    //*******************************************begin receipt tab*******************************************

    /**
     * Master method for the receipt tab. Sets all the data for the receipt tab. Currently only shows
     * receipts from beginning of the current year to the current month.
     *
     * TODO: Need to make receipt tab go further back than only the current year
     */
    void setReceiptData() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                int month = Calendar.getInstance().get(Calendar.MONTH) + 1;
                SQLiteDatabase db = DataHelper.getInstance(context).getReadableDatabase("somePassword123");
                String[] column = new String[]{DataHelper.DAY, DataHelper.MONTH, DataHelper.YEAR, DataHelper.PATH};

                receiptLayout.removeAllViews();

                for (int i = month; i > 0; i--) {
                    TextView currentMonth = new TextView(context);
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
    } //setReceiptData

    /**
     * Queries the db for rows that have the months passed in. It will generate a linear layout with
     * a thumbnail of the receipt, and the full date below this thumbnail.
     *
     * @param month month argument
     * @param readDB database instance
     * @param columnArray which columns to be viewed
     * @return linear layout to be appended
     */
    LinearLayout getImages(String[] month, SQLiteDatabase readDB, String[] columnArray) {
        LinearLayout imagesLayout = new LinearLayout(context);
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

                LinearLayout imageLayout = new LinearLayout(context);
                imageLayout.setOrientation(LinearLayout.VERTICAL);
                imageLayout.setBackgroundColor(Color.parseColor("#FFEFC4"));
                imageLayout.setLayoutParams(params);
                imageLayout.setPadding(5, 0, 0, 5);

                String date = cursor.getString(0) + "/" + cursor.getString(1) + "/" + cursor.getString(2);
                Bitmap receiptThumbnail = ThumbnailUtils.extractThumbnail(BitmapFactory.decodeFile(cursor.getString(3)), 128, 128);

                ImageView receiptView = new ImageView(context);
                receiptView.setTag(cursor.getString(3));
                receiptView.setOnClickListener(viewReceiptDetails);

                receiptView.setImageBitmap(receiptThumbnail);
                receiptView.setPadding(10, 0, 10, 0);

                TextView dateView = new TextView(context);
                dateView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                dateView.setText(date);

                imageLayout.addView(receiptView);
                imageLayout.addView(dateView);
                imagesLayout.addView(imageLayout);

                cursor.moveToNext();
            } //end while
        } //end if

        if (!cursor.isClosed())
            cursor.close();

        return imagesLayout;
    } //getImages

    View.OnClickListener viewReceiptDetails = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            viewReceiptData(v.getTag().toString(), v);
        }
    };

    /**
     * Instantiates fragment for dialog and passes path for the receipt which is not currently used,
     * implementation for it needs to be rethought of. It also passes the bitmap of the image
     * for it to be viewed in the fragment instead of just looking at a thumbnail.
     *
     * @param path image path
     * @param view button view
     */
    void viewReceiptData(final String path, final View view) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                File receiptFile = new File(path);
                BitmapFactory.Options options = new BitmapFactory.Options();
                Bitmap bitmap = BitmapFactory.decodeFile(receiptFile.getAbsolutePath(), options);

                ReceiptDialogFragment receiptDialogFragment = new ReceiptDialogFragment();

                Bundle bundle = new Bundle();
                bundle.putParcelable("bitmap", bitmap);
                bundle.putString("path", view.getTag().toString());
                receiptDialogFragment.setArguments(bundle);

                receiptDialogFragment.show(getSupportFragmentManager(), "ReceiptDialog");
            }
        });
    } //viewReceiptData

    //*******************************************end receipt tab*******************************************







    //*******************************************begin expense tab*******************************************

    /**
     * Master method for expense tab. Queries the database either with no selection or the current
     * month. Reading each row will add and store the amounts to the proper time intervals. Adds the
     * views to the display and a text bubble that tells you how much time is left and how much
     * you have spent up to date.
     *
     * @param option Determines query selection
     */
    void setExpenseData(final int option) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                expenseDataLayout.removeAllViews();

                double total = 0.0;
                double monthTotal = 0.0;
                String[] args;
                String selection,
                       orderBy,
                       totalText;

                int currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1,
                    daysInMonth, year = 0;

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

                SQLiteDatabase db = DataHelper.getInstance(context).getReadableDatabase("somePassword123");
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
                    double[] weeks  = new double[5];
                    double[] months = new double [12];

                    cursor.moveToFirst();

                    while (!cursor.isAfterLast()) {
                        int day       = Integer.parseInt(cursor.getString(0)),
                            month     = Integer.parseInt(cursor.getString(1));
                        double amount = Double.parseDouble(cursor.getString(2));

                        year = Integer.parseInt(cursor.getString(3));

                        if (option == 1 && month != currentMonth) {
                            months[month - 1] += amount;
                            monthTotal += amount;
                        }
                        else {
                            double[] weekAmount = separateIntoWeeks(day, amount);
                            weeks[(int)weekAmount[0]] += weekAmount[1];
                            if (option == 1)
                                monthTotal += amount;
                            else
                                total += amount;
                        } //end else

                        cursor.moveToNext();
                    } //end while

                    if (Build.VERSION.SDK_INT >= 26) {
                        YearMonth yearMonthObject = YearMonth.of(1999, 2);
                        daysInMonth = yearMonthObject.lengthOfMonth();
                    }
                    else {
                        Calendar mycal = new GregorianCalendar(year, currentMonth - 1, 1);
                        daysInMonth = mycal.getActualMaximum(Calendar.DAY_OF_MONTH);
                    }

                    if (option == 0) {
                        setExpenseLayout(5, weeks);
                        totalText  = "$" + total + " spent with " +
                                (daysInMonth - Calendar.getInstance().get(Calendar.DAY_OF_MONTH)) +
                                " days left in the month";
                    }
                    else {
                        setExpenseLayout(12, months);
                        totalText = "$" + monthTotal + " spent with " + (12 - currentMonth) +
                                " months and " +
                                (daysInMonth - Calendar.getInstance().get(Calendar.DAY_OF_MONTH)) +
                                " days left in the year";
                    }

                    TextView totalView = new TextView(context);
                    totalView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                    totalView.setPadding(0, 20, 0, 0);
                    totalView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
                    totalView.setText(totalText);
                    expenseDataLayout.addView(totalView);
                } //end if

                if (!cursor.isClosed())
                    cursor.close();
                db.close();
            } //run
        });
    } //setExpenseData

    /**
     * Loops through each time interval and creates a text view for the time interval and total amount
     * in that time interval then adds it to the layout.
     *
     * @param limit upper limit for for loop
     * @param amount array of dollar amount per time period
     */
    void setExpenseLayout(int limit, double[] amount) {
        String timeInterval;
        for (int i = 0 ; i < limit ; i ++) {
            TextView intervalView = new TextView(context);
            intervalView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 25);
            intervalView.setPadding(30, 15, 0, 0);

            if (amount[i] == 0)
                intervalView.setVisibility(View.INVISIBLE);

            if (limit == 5)
                timeInterval = "Week " + (i + 1) + ": $" + amount[i];
            else
                timeInterval = getMonthText(i + 1) + ": $" + amount[i];
            intervalView.setText(timeInterval);

            expenseDataLayout.addView(intervalView);
        } //end for
    } //setExpenseLayout

    /**
     * Separates the day into a certain week number
     *
     * @param day number of day
     * @param amount receipt amount
     * @return double array containing week number and price amount
     */
    static double[] separateIntoWeeks(int day, double amount) {
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
    } //separateIntoWeeks

    //*******************************************end expense tab*******************************************




    /*
        TODO: So far trying to include it in the dialog fragment does not work out because of application context error.
     */
    void checkDeleteDialog(final String path) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String text = "Are you sure you want to delete this receipt information from the app?",
                        yes  = "Delete",
                        no   = "Cancel";
                if (context != null) {

                    final AlertDialog.Builder builder = new AlertDialog.Builder(context);
                    builder.setMessage(text)
                            .setPositiveButton(yes, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    SQLiteDatabase db = DataHelper.getInstance(context).getReadableDatabase("somePassword123");
                                    String[] column = new String[] {"rule", DataHelper.PATH},
                                            args   = new String[] {path};
                                    String selection = DataHelper.PATH + "=?";

                                    Cursor cursor = db.query(
                                            DataHelper.DB_TABLE,
                                            column,
                                            selection,
                                            args,
                                            null,
                                            null,
                                            null);

                                    if (cursor.getCount() > 0) {
                                        cursor.moveToFirst();

                                        while (!cursor.isAfterLast()) {
                                            System.out.println(cursor.getString(0));
                                            System.out.println(cursor.getString(1));
                                            cursor.moveToNext();
                                        } //end while
                                    } //end if

                                    if (!cursor.isClosed())
                                        cursor.close();
                                    db.close();
                                }
                            })
                            .setNegativeButton(no, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.dismiss();
                                }
                            });
                    builder.show();
                }
            }
        });
    }

    public class UnitTests {

        public UnitTests() {

        }
        public void checkIfDoubleArray () {
            double[] expected = new double[] {0, 5.1},
                    method   = MainActivity.separateIntoWeeks(5, 5.1);

            Assert.assertArrayEquals(expected, method, 0);
        }

    }




    //*******************************************begin tess api*******************************************

    private void copyTessDataForTextRecognizor() {
        Runnable run = new Runnable() {
            @Override
            public void run() {
                AssetManager assetManager = context.getAssets();
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
                        Toast.makeText(context, e.toString(), Toast.LENGTH_SHORT).show();
                    }
                }
            }
        };
        new Thread(run).start();
    }

    private String tessDataPath() {
        return context.getExternalFilesDir(null) + "/tessdata/";
    }

    private String getTessDataParentDirectory() {
        return Objects.requireNonNull(getApplicationContext().getExternalFilesDir(null)).getPath();
    }

    private class AsyncTessAPITask extends AsyncTask<Bitmap, Integer, String> {

        private final String TAG = MainActivity.class.getSimpleName();
        private TessBaseAPI tessBaseAPI;

        @Override
        protected String doInBackground(Bitmap... bitmaps) {
            Bitmap imageBitmap = bitmaps[0];

            try {
                tessBaseAPI = new TessBaseAPI();
            } //end try
            catch (Exception e) {
                Log.e(TAG, e.getMessage());
            } //end catch

            tessBaseAPI.init(getTessDataParentDirectory(), "eng");
            tessBaseAPI.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO);
            tessBaseAPI.setImage(imageBitmap);
            String returnString = "No result";

            try {
                returnString = tessBaseAPI.getUTF8Text();
            } //end try
            catch (Exception e) {
                Log.e(TAG, e.getMessage());
            } //end catch
            tessBaseAPI.end();
            return returnString.replace("\\s", "");
        } //doInBackground


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
            } //end if
            if (dateMatcherTwo.find()) {
                String dateResult = dateMatcherTwo.group(0).replaceAll("/-~'—\\s", "-");
                String year = dateResult.substring(0, 3);
                String month = dateResult.substring(5, 6);
                String day = dateResult.substring(8, 9);
                setTextData[0] = month;
                setTextData[1] = day;
                setTextData[2] = year;
            } //end if

            if (amountMatcher.find()) {
                String amountResult = amountMatcher.group(0).trim().replaceAll("(?i)total ", "");
                amountResult = amountResult.replaceAll("[^\\d$]+", ".");
                setTextData[3] = amountResult;
            } //end if
            else {
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
                } //end if
            } //end else
            setLayoutVisibility(R.id.scannerLayout, true);
            setOCRText(setTextData);
        } //onPostExecute
    } //AsyncTessAPITask
} //MainActivity
