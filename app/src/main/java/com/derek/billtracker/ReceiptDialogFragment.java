package com.derek.billtracker;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;


/**
 * A simple {@link DialogFragment} subclass.
 */
public class ReceiptDialogFragment extends DialogFragment {

    private static final String TAG = "ReceiptDialog";

    private ImageView mActionRotateRight, mActionRotateLeft;
    private TextView mActionExit;

    private ImageView receiptImage;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment

        View view = inflater.inflate(R.layout.fragment_receipt_dialog, container, false);

        mActionRotateLeft = view.findViewById(R.id.rotateImageLeft);
        mActionRotateRight = view.findViewById(R.id.rotateImageRight);
        mActionExit = view.findViewById(R.id.exit);

        receiptImage = view.findViewById(R.id.receiptView);

        Bundle bundle = this.getArguments();
        if (bundle != null) {
            Bitmap bitmap = bundle.getParcelable("bitmap");
            receiptImage.setImageBitmap(bitmap);
        }

        mActionExit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "Closing Dialog");
                getDialog().dismiss();
            }
        });

        mActionRotateRight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                receiptImage.setRotation(receiptImage.getRotation() - 90);
            }
        });

        mActionRotateLeft.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                receiptImage.setRotation(receiptImage.getRotation() + 90);
            }
        });

        return view;
    }
}
