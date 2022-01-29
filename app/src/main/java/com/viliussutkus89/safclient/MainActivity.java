/*
Copyright (c) 2022 ViliusSutkus89.com
https://www.viliussutkus89.com/posts/testing-storage-access-framework-saf-clients/

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

package com.viliussutkus89.safclient;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AppCompatActivity;
import androidx.test.espresso.IdlingResource;
import androidx.test.espresso.idling.CountingIdlingResource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

public class MainActivity extends AppCompatActivity {

    @Nullable
    private CountingIdlingResource m_idlingResource;

    @VisibleForTesting
    @NonNull
    IdlingResource getIdlingResource() {
        if (null == m_idlingResource) {
            m_idlingResource = new CountingIdlingResource("MainActivity.m_idlingResource");
        }
        return m_idlingResource;
    }

    public static String readFromUri(Uri uri, ContentResolver contentResolver) throws IOException {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            try (InputStream inputStream = contentResolver.openInputStream(uri)) {
                final byte[] buffer = new byte[32];
                int didRead;
                while (0 < (didRead = inputStream.read(buffer))) {
                    byteArrayOutputStream.write(buffer, 0, didRead);
                }
                return byteArrayOutputStream.toString();
            }
        }
    }

    public static void writeToUri(Uri uri, ContentResolver contentResolver, String data) throws IOException {
        try (OutputStream outputStream = contentResolver.openOutputStream(uri, "wt")) {
            outputStream.write(data.getBytes());
        }
    }

    private final ActivityResultLauncher<String> m_getContent = registerForActivityResult(
            new ActivityResultContracts.GetContent(), selectedDocument -> {
                if (null != selectedDocument) {
                    TextView tv = findViewById(R.id.text_ACTION_GET_CONTENT);
                    try {
                        tv.setText(readFromUri(selectedDocument, getContentResolver()));
                    } catch (IOException e) {
                        Toast.makeText(this, "Reading failed! " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }

                if (null != m_idlingResource) {
                    m_idlingResource.decrement();
                }
            }
    );

    private final ActivityResultLauncher<String[]> m_openDocument = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), selectedDocument -> {
                if (null != selectedDocument) {
                    TextView tv = findViewById(R.id.text_ACTION_OPEN_DOCUMENT);
                    try {
                        String textInFile = readFromUri(selectedDocument, getContentResolver());
                        tv.setText(textInFile);
                        String modifiedText = flipStringCase(textInFile);
                        writeToUri(selectedDocument, getContentResolver(), modifiedText);
                    } catch (IOException e) {
                        Toast.makeText(this, "Reading or writing failed! " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }

                    if (null != m_idlingResource) {
                        m_idlingResource.decrement();
                    }
                }
            }
    );

    private final ActivityResultLauncher<String> m_createDocument = registerForActivityResult(
            new ActivityResultContracts.CreateDocument(),
            selectedOutputDocument -> {
                if (null != selectedOutputDocument) {
                    Editable text = ((EditText) findViewById(R.id.text_ACTION_CREATE_DOCUMENT)).getText();
                    String enteredText = text.toString();
                    text.clear();
                    try {
                        writeToUri(selectedOutputDocument, getContentResolver(), enteredText);
                    } catch (IOException e) {
                        Toast.makeText(this, "Writing failed! " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }

                    if (null != m_idlingResource) {
                        m_idlingResource.decrement();
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.button_ACTION_GET_CONTENT).setOnClickListener(view -> {
            if (null != m_idlingResource) {
                m_idlingResource.increment();
            }

            String mimeType = "*/*";
            m_getContent.launch(mimeType);
        });

        // ACTION_OPEN_DOCUMENT and ACTION_CREATE_DOCUMENT are available since KitKat
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            findViewById(R.id.button_ACTION_OPEN_DOCUMENT).setOnClickListener(view -> {
                if (null != m_idlingResource) {
                    m_idlingResource.increment();
                }

                String mimeType = "*/*";
                m_openDocument.launch(new String[]{mimeType});
            });

            findViewById(R.id.button_ACTION_CREATE_DOCUMENT).setOnClickListener(view -> {
                if (null != m_idlingResource) {
                    m_idlingResource.increment();
                }

                String suggestedOutputFilename = "sampleFile.txt";
                m_createDocument.launch(suggestedOutputFilename);
            });
        } else {
            findViewById(R.id.button_ACTION_OPEN_DOCUMENT).setEnabled(false);
            findViewById(R.id.button_ACTION_CREATE_DOCUMENT).setEnabled(false);
        }
    }

    @VisibleForTesting
    public static String flipStringCase(String str) {
        StringBuilder stringBuilder = new StringBuilder(str.length());
        for (int i = 0; i < str.length(); i++) {
            char letter = str.charAt(i);
            if (Character.isUpperCase(letter)) {
                letter = Character.toLowerCase(letter);
            } else if (Character.isLowerCase(letter)) {
                letter = Character.toUpperCase(letter);
            }
            stringBuilder.append(letter);
        }
        return stringBuilder.toString();
    }
}
