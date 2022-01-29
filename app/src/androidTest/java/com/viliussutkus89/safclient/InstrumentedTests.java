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

import static androidx.test.espresso.Espresso.closeSoftKeyboard;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasAction;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.isEmptyString;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import androidx.core.content.FileProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.IdlingRegistry;
import androidx.test.espresso.IdlingResource;
import androidx.test.espresso.intent.Intents;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Random;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class InstrumentedTests {

    private static class RandomString {
        private static final Random m_random = new Random();
        private static final char[] m_letters = new char['z' - 'a'];

        static {
            for (int i = 0; i < m_letters.length; i++) {
                m_letters[i] = (char) ('a' + i);
            }
        }

        static String generate(int length) {
            StringBuilder stringBuilder = new StringBuilder(length);
            for (int i = 0; i < length; i++) {
                int randomLetterId = m_random.nextInt(m_letters.length);
                char randomLetter = m_letters[randomLetterId];
                stringBuilder.append(randomLetter);
            }
            return stringBuilder.toString();
        }
    }

    private static class RandomFile {
        final String m_generatedContent = RandomString.generate(16);
        final File m_file;

        RandomFile() {
            Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
            m_file = generateFilenameInCache(appContext, "shared");
        }

        private File generateFilenameInCache(Context ctx, String subDirectory) {
            File cacheDir = ctx.getCacheDir();
            File subDir = new File(cacheDir, subDirectory);
            subDir.mkdir();

            while (true) {
                File fileInCache = new File(subDir, RandomString.generate(8));
                if (!fileInCache.exists()) {
                    return fileInCache;
                }
            }
        }

        void writeGeneratedContentToFile() throws IOException {
            try (OutputStream outputStream = new FileOutputStream(m_file)) {
                try (OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream)) {
                    outputStreamWriter.write(m_generatedContent);
                }
            }
        }

        String readFromFile() throws IOException {
            try (OutputStream outputStream = new ByteArrayOutputStream(16)) {
                try (InputStream inputStream = new FileInputStream(m_file)) {
                    final byte[] buffer = new byte[16];
                    int didRead;
                    while (0 < (didRead = inputStream.read(buffer))) {
                        outputStream.write(buffer, 0, didRead);
                    }
                }
                return outputStream.toString();
            }
        }

        Uri getUriFromFileProvider() {
            Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
            String authority = appContext.getPackageName() + ".instrumentedTestsProvider";
            return FileProvider.getUriForFile(appContext, authority, m_file);
        }
    }

    private IdlingResource m_idlingResource;

    @Before
    public void setUp() {
        ActivityScenario.launch(MainActivity.class).onActivity(activity -> {
            m_idlingResource = activity.getIdlingResource();
            IdlingRegistry.getInstance().register(m_idlingResource);

            // Close system dialogs which may cover our Activity.
            // Happens frequently on slow emulators.
            activity.sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
        });

        Intents.init();
    }

    @After
    public void tearDown() {
        Intents.release();

        if (null != m_idlingResource) {
            IdlingRegistry.getInstance().unregister(m_idlingResource);
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.KITKAT)
    public void test_ACTION_CREATE_DOCUMENT() throws IOException {
        RandomFile randomFile = new RandomFile();
        Intents.intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(
                new Instrumentation.ActivityResult(Activity.RESULT_OK,
                        new Intent()
                                .setData(randomFile.getUriFromFileProvider())
                                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                )
        );

        onView(withId(R.id.text_ACTION_CREATE_DOCUMENT))
                .check(matches(withText(isEmptyString())))
                .perform(typeText(randomFile.m_generatedContent));

        closeSoftKeyboard();

        onView(withId(R.id.button_ACTION_CREATE_DOCUMENT)).perform(click());

        onView(withId(R.id.text_ACTION_CREATE_DOCUMENT)).check(matches(withText(isEmptyString())));

        Assert.assertEquals(randomFile.m_generatedContent, randomFile.readFromFile());
        Assert.assertTrue(randomFile.m_file.delete());
    }

    @Test
    public void test_ACTION_GET_CONTENT() throws IOException {
        RandomFile randomFile = new RandomFile();
        randomFile.writeGeneratedContentToFile();
        Intents.intending(hasAction(Intent.ACTION_GET_CONTENT)).respondWith(
                new Instrumentation.ActivityResult(Activity.RESULT_OK,
                        new Intent()
                                .setData(randomFile.getUriFromFileProvider())
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                )
        );

        onView(withId(R.id.text_ACTION_GET_CONTENT)).check(matches(withText(isEmptyString())));
        onView(withId(R.id.button_ACTION_GET_CONTENT)).perform(click());
        onView(withId(R.id.text_ACTION_GET_CONTENT)).check(matches(withText(randomFile.m_generatedContent)));
        Assert.assertTrue(randomFile.m_file.delete());
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.KITKAT)
    public void test_ACTION_OPEN_DOCUMENT() throws IOException {
        RandomFile randomFile = new RandomFile();
        randomFile.writeGeneratedContentToFile();
        Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(
                new Instrumentation.ActivityResult(Activity.RESULT_OK,
                        new Intent()
                                .setData(randomFile.getUriFromFileProvider())
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                )
        );

        onView(withId(R.id.text_ACTION_OPEN_DOCUMENT)).check(matches(withText(isEmptyString())));
        onView(withId(R.id.button_ACTION_OPEN_DOCUMENT)).perform(click());
        onView(withId(R.id.text_ACTION_OPEN_DOCUMENT)).check(matches(withText(randomFile.m_generatedContent)));
        Assert.assertTrue(randomFile.m_file.delete());
    }
}
