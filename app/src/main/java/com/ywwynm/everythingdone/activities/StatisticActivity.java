package com.ywwynm.everythingdone.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.ywwynm.everythingdone.Definitions;
import com.ywwynm.everythingdone.EverythingDoneApplication;
import com.ywwynm.everythingdone.R;
import com.ywwynm.everythingdone.adapters.StatisticAdapter;
import com.ywwynm.everythingdone.database.HabitDAO;
import com.ywwynm.everythingdone.database.ReminderDAO;
import com.ywwynm.everythingdone.database.ThingDAO;
import com.ywwynm.everythingdone.fragments.LoadingDialogFragment;
import com.ywwynm.everythingdone.helpers.AttachmentHelper;
import com.ywwynm.everythingdone.helpers.CheckListHelper;
import com.ywwynm.everythingdone.model.Habit;
import com.ywwynm.everythingdone.model.Reminder;
import com.ywwynm.everythingdone.model.Thing;
import com.ywwynm.everythingdone.model.ThingsCounts;
import com.ywwynm.everythingdone.utils.BitmapUtil;
import com.ywwynm.everythingdone.utils.DateTimeUtil;
import com.ywwynm.everythingdone.utils.DisplayUtil;
import com.ywwynm.everythingdone.utils.FileUtil;
import com.ywwynm.everythingdone.utils.LocaleUtil;
import com.ywwynm.everythingdone.utils.VersionUtil;
import com.ywwynm.everythingdone.views.FloatingActionButton;

import org.joda.time.DateTime;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class StatisticActivity extends EverythingDoneBaseActivity {

    public static final String TAG = "StatisticActivity";

    private EverythingDoneApplication mApplication;

    private ThingsCounts mThingsCounts;
    private ThingDAO mThingDAO;

    private float mScreenDensity;
    private float mHeaderHeight;

    private View mStatusbar;
    private Toolbar mActionbar;
    private TextView mTitle;
    private View mActionbarShadow;

    private ImageView mIvHeader;
    private ScrollView mScrollView;

    private FloatingActionButton mFab;

    private LoadingDialogFragment mLdf;

    private List<File> mSharedFiles;

    @Override
    protected void onDestroy() {
        super.onDestroy();
        for (File sharedFile : mSharedFiles) {
            FileUtil.deleteFile(sharedFile);
        }
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.activity_statistic;
    }

    @Override
    protected void initMembers() {
        mApplication = (EverythingDoneApplication) getApplication();

        mThingsCounts = ThingsCounts.getInstance(mApplication);
        mThingDAO = ThingDAO.getInstance(mApplication);

        mScreenDensity = DisplayUtil.getScreenDensity(mApplication);

        final int screenWidth = DisplayUtil.getScreenSize(mApplication).x;
        mHeaderHeight = screenWidth * 1080f / 1920;

        mSharedFiles = new ArrayList<>();
    }

    @Override
    protected void findViews() {
        mStatusbar = findViewById(R.id.view_status_bar);
        mActionbar = (Toolbar) findViewById(R.id.actionbar);
        mTitle = (TextView) findViewById(R.id.tv_title_statistic);
        mActionbarShadow = findViewById(R.id.actionbar_shadow);

        mIvHeader = (ImageView) findViewById(R.id.iv_header_statistic);
        mScrollView = (ScrollView) findViewById(R.id.sv_statistic);

        mFab = (FloatingActionButton) findViewById(R.id.fab_share);
    }

    @Override
    protected void initUI() {
        initHeaderUI();
        initStartFromUI();
        initFinishedCreatedUI();
        initNoteUI();
        initReminderUI();
        initHabitUI();
        initGoalUI();
    }

    private void initHeaderUI() {
        if (VersionUtil.hasKitKatApi()) {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams)
                    mStatusbar.getLayoutParams();
            params.height = DisplayUtil.getStatusbarHeight(this);
            mStatusbar.requestLayout();
        }

        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) mIvHeader.getLayoutParams();
        lp.height = (int) mHeaderHeight;
        mIvHeader.requestLayout();

        float mt = mHeaderHeight - mScreenDensity * 28;
        if (!VersionUtil.hasLollipopApi()) {
            mt -= mScreenDensity * 28;
        }
        FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) mFab.getLayoutParams();
        flp.topMargin = (int) mt;
        mFab.requestLayout();
    }

    private void initStartFromUI() {
        SharedPreferences metaData = getSharedPreferences(
                Definitions.MetaData.META_DATA_NAME, MODE_PRIVATE);
        long time = metaData.getLong(Definitions.MetaData.KEY_START_USING_TIME, 0);
        DateTime dt = new DateTime(time);
        int gap = DateTimeUtil.calculateTimeGap(
                time, System.currentTimeMillis(), Calendar.DATE) + 1;
        StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.statistic_start_from_part_1));
        if (LocaleUtil.isChinese(mApplication)) {
            sb.append(dt.toString(" yyyy 年 M 月 d 日"))
                    .append(getString(R.string.statistic_start_from_part_2))
                    .append(" ").append(gap).append(" ");
        } else {
            sb.append(dt.toString(" MMM d, yyyy"))
                    .append(getString(R.string.statistic_start_from_part_2));
            if (gap <= 1) {
                sb.append(" this day");
            } else {
                sb.append(" these ").append(gap).append(" days ");
            }
        }
        sb.append(getString(R.string.statistic_start_from_part_3));

        TextView tv = (TextView) findViewById(R.id.tv_start_from_statistic);
        tv.setText(sb.toString());
    }

    private void initFinishedCreatedUI() {
        new FinishedCreatedTask().execute();
    }

    private void initNoteUI() {
        int u = mThingsCounts.getCount(Thing.NOTE, Thing.UNDERWAY);
        int f = mThingsCounts.getCount(Thing.NOTE, Thing.FINISHED);
        int d = mThingsCounts.getCount(Thing.NOTE, Thing.DELETED);
        if (u != 0 || f != 0 || d != 0) {
            new NoteTask().execute();
        } else {
            findViewById(R.id.tv_note_record_statistic).setVisibility(View.GONE);
            findViewById(R.id.cv_note_record_statistic).setVisibility(View.GONE);
        }
    }

    private void initReminderUI() {
        int u = mThingsCounts.getCount(Thing.REMINDER, Thing.UNDERWAY);
        int f = mThingsCounts.getCount(Thing.REMINDER, Thing.FINISHED);
        int d = mThingsCounts.getCount(Thing.REMINDER, Thing.DELETED);
        if (u != 0 || f != 0 || d != 0) {
            new ReminderTask().execute();
        } else {
            findViewById(R.id.tv_reminder_record_statistic).setVisibility(View.GONE);
            findViewById(R.id.cv_reminder_record_statistic).setVisibility(View.GONE);
        }
    }

    private void initHabitUI() {
        int u = mThingsCounts.getCount(Thing.HABIT, Thing.UNDERWAY);
        int f = mThingsCounts.getCount(Thing.HABIT, Thing.FINISHED);
        int d = mThingsCounts.getCount(Thing.HABIT, Thing.DELETED);
        if (u != 0 || f != 0 || d != 0) {
            new HabitTask().execute();
        } else {
            findViewById(R.id.tv_habit_record_statistic).setVisibility(View.GONE);
            findViewById(R.id.cv_habit_record_statistic).setVisibility(View.GONE);
        }
    }

    private void initGoalUI() {
        int u = mThingsCounts.getCount(Thing.GOAL, Thing.UNDERWAY);
        int f = mThingsCounts.getCount(Thing.GOAL, Thing.FINISHED);
        int d = mThingsCounts.getCount(Thing.GOAL, Thing.DELETED);

        if (u != 0 || f != 0 || d != 0) {
            new GoalTask().execute();
        } else {
            findViewById(R.id.tv_goal_record_statistic).setVisibility(View.GONE);
            findViewById(R.id.cv_goal_record_statistic).setVisibility(View.GONE);
        }
    }

    @Override
    protected void setActionbar() {
        setSupportActionBar(mActionbar);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setTitle(null);
        mActionbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    @Override
    protected void setEvents() {
        mScrollView.getViewTreeObserver().addOnScrollChangedListener(
                new ViewTreeObserver.OnScrollChangedListener() {

                    @Override
                    public void onScrollChanged() {
                        updateFabState();
                        updateActionbarState();
                    }
        });

        mFab.setOnClickListener(new View.OnClickListener() {

            final int color = ContextCompat.getColor(mApplication, R.color.blue_grey_deep_grey);

            @Override
            public void onClick(View v) {
                if (mLdf == null) {
                    mLdf = new LoadingDialogFragment();
                    mLdf.setAccentColor(color);
                    mLdf.setTitle(getString(R.string.please_wait));
                    mLdf.setContent(getString(R.string.generating_screen_shot));
                }
                mLdf.show(getFragmentManager(), LoadingDialogFragment.TAG);

                new ScreenshotTask().execute();

            }
        });
    }

    private void updateFabState() {
        int statusbarSize = VersionUtil.hasKitKatApi() ?
            DisplayUtil.getStatusbarHeight(StatisticActivity.this) : 0;
        int scrollY = mScrollView.getScrollY();
        int actionbarSize = mActionbar.getHeight();
        float fabY = mHeaderHeight - statusbarSize - actionbarSize - actionbarSize;
        if (scrollY >= fabY) {
            mFab.shrink();
        } else {
            mFab.spread();
        }
    }

    private void updateActionbarState() {
        int statusbarSize = VersionUtil.hasKitKatApi() ?
                DisplayUtil.getStatusbarHeight(mApplication) : 0;
        int scrollY = mScrollView.getScrollY();
        int color = ContextCompat.getColor(mApplication, R.color.blue_grey_deep_grey);
        int actionbarSize = mActionbar.getHeight();
        float abSY = mHeaderHeight - statusbarSize - 2 * actionbarSize;
        float abTY = abSY + actionbarSize;
        if (scrollY <= abSY) {
            mStatusbar.setBackgroundColor(0);
            mActionbar.setBackgroundColor(0);
            mTitle.setAlpha(0);
            mActionbarShadow.setAlpha(0);
        } else if (scrollY >= abTY) {
            mStatusbar.setBackgroundColor(color);
            mActionbar.setBackgroundColor(color);
            mTitle.setAlpha(1.0f);
            mActionbarShadow.setAlpha(1.0f);
        } else {
            float progress = (scrollY - abSY) / (abTY - abSY);
            color = DisplayUtil.getTransparentColor(color, (int) (progress * 255));
            mStatusbar.setBackgroundColor(color);
            mActionbar.setBackgroundColor(color);
            mTitle.setAlpha(progress);
            mActionbarShadow.setAlpha(0);
        }
    }

    private String[] getStrsForNoteRecord() {
        final String TITLE   = Definitions.Database.COLUMN_TITLE_THINGS;
        final String CONTENT = Definitions.Database.COLUMN_CONTENT_THINGS;
        final String ATTACH  = Definitions.Database.COLUMN_ATTACHMENT_THINGS;

        String[] strs = new String[4];
        int[] counts = new int[4];
        Cursor cursor = mThingDAO.getThingsCursor("type=" + Thing.NOTE);
        while (cursor.moveToNext()) {
            String title = cursor.getString(cursor.getColumnIndex(TITLE));
            String content = cursor.getString(cursor.getColumnIndex(CONTENT));
            counts[0] += title.length();
            if (CheckListHelper.isCheckListStr(content)) {
                counts[0] += CheckListHelper.toContentStr(content, "", "")
                        .replaceAll("\n", "").length();
            } else {
                counts[0] += content.replaceAll("\n", "").length();
            }

            String attachment = cursor.getString(cursor.getColumnIndex(ATTACH));
            for (int i = 1; i < counts.length; i++) {
                counts[i] += countOfKey(attachment, AttachmentHelper.SIGNAL + (i - 1));
            }
        }
        cursor.close();
        for (int i = 0; i < counts.length; i++) {
            strs[i] = String.valueOf(counts[i]);
        }
        return strs;
    }

    private String[] getStrsForHabitRecord() {
        // 习惯养成率
        // 完成/总次数
        // 完成率
        // 最长连续完成次数
        // 最长坚持周期数
        String[] strs = new String[5];
        strs[0] = mThingsCounts.getCompletionRate(Definitions.LimitForGettingThings.HABIT_UNDERWAY);

        int fCount = 0; // finished times
        int tCount = 0; // total record times
        int maxCft  = 0; // longest continuous finish times
        int maxPit = 0; // longest persist in T

        HabitDAO hDao = HabitDAO.getInstance(mApplication);
        Cursor cursor = mThingDAO.getThingsCursor("type=" + Thing.HABIT);
        while (cursor.moveToNext()) {
            long id = cursor.getLong(
                    cursor.getColumnIndex(Definitions.Database.COLUMN_ID_THINGS));
            Habit habit = hDao.getHabitById(id);
            String record = habit.getRecord();
            fCount += countOfKey(record, "1");
            tCount += record.length();

            int cft = longestKeySequenceSize(record, '1');
            if (cft > maxCft) {
                maxCft = cft;
            }

            int pit = habit.getPersistInT();
            if (pit > maxPit) {
                maxPit = pit;
            }
        }
        cursor.close();

        strs[1] = fCount + " / " + tCount;
        strs[2] = LocaleUtil.getPercentStr(fCount, tCount);
        strs[3] = String.valueOf(maxCft);
        strs[4] = String.valueOf(maxPit);
        return strs;
    }

    private String[] getStrsForReminderGoalRecord(boolean isReminder) {
        // 完成率
        // 平均提醒时长
        // 平均完成时间
        // 提前完成的比例
        String[] strs = new String[4];
        if (isReminder) {
            strs[0] = mThingsCounts.getCompletionRate(
                    Definitions.LimitForGettingThings.REMINDER_UNDERWAY);
        } else {
            strs[0] = mThingsCounts.getCompletionRate(
                    Definitions.LimitForGettingThings.GOAL_UNDERWAY);
        }

        long tNtfMillis = 0; // total notify time length in milliseconds
        long tFinTime   = 0; // total finish time in milliseconds
        int inAdvcCount = 0; // count of reminders that have been finished in advance
        int fCount = 0;

        ReminderDAO rDao = ReminderDAO.getInstance(mApplication);
        Cursor cursor = mThingDAO.getThingsCursor("type=" +
                (isReminder ? Thing.REMINDER : Thing.GOAL));
        while (cursor.moveToNext()) {
            long id = cursor.getLong(
                    cursor.getColumnIndex(Definitions.Database.COLUMN_ID_THINGS));
            Reminder reminder = rDao.getReminderById(id);
            tNtfMillis += reminder.getNotifyMillis();

            int state = cursor.getInt(
                    cursor.getColumnIndex(Definitions.Database.COLUMN_STATE_THINGS));
            if (state == Thing.FINISHED) {
                fCount++;
                long updateTime = reminder.getUpdateTime();
                long finishTime = cursor.getLong(
                        cursor.getColumnIndex(Definitions.Database.COLUMN_FINISH_TIME_THINGS));
                tFinTime += (finishTime - updateTime);

                if (finishTime < reminder.getNotifyTime()
                        && reminder.getState() != Reminder.REMINDED) {
                    inAdvcCount++;
                }
            }
        }
        cursor.close();

        int tCount = cursor.getCount();
        if (isReminder) {
            strs[1] = DateTimeUtil.getTimeLengthStr(tNtfMillis / tCount, mApplication);
        } else {
            strs[1] = DateTimeUtil.getTimeLengthStrOnlyDay(tNtfMillis / tCount, mApplication);
        }

        if (fCount == 0) {
            strs[2] = "∞";
        } else {
            if (isReminder) {
                strs[2] = DateTimeUtil.getTimeLengthStr(tFinTime / fCount, mApplication);
            } else {
                strs[2] = DateTimeUtil.getTimeLengthStrOnlyDay(tFinTime / fCount, mApplication);
            }
        }

        strs[3] = LocaleUtil.getPercentStr(inAdvcCount, fCount);

        return strs;
    }

    private int countOfKey(String src, String key) {
        if (src == null) {
            return 0;
        }
        final int lenBefore = src.length();
        final int lenAfter = src.replaceAll(key, "").length();
        return (lenBefore - lenAfter) / key.length();
    }

    private int longestKeySequenceSize(String src, char key) {
        int longest = 0;
        int tempCount = 0;
        final int len = src.length();
        for (int i = 0; i < len; i++) {
            if (src.charAt(i) == key) {
                tempCount++;
            } else {
                tempCount = 0;
            }
            if (tempCount > longest) {
                longest = tempCount;
            }
        }
        return longest;
    }

    private File getScreenShot() {
        final int count = mScrollView.getChildCount();
        int height = 0;
        for (int i = 0; i < count; i++) {
            View view = mScrollView.getChildAt(i);
            height += view.getHeight();
        }

        // get screen shot bitmap
        Bitmap bitmap = Bitmap.createBitmap(mScrollView.getWidth(), height,
                Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(bitmap);
        mScrollView.draw(canvas);

        String name = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        name += ".jpeg";
        return BitmapUtil.saveBitmapToStorage(FileUtil.TEMP_PATH, name, bitmap);
    }

    class FinishedCreatedTask extends AsyncTask<Object, Object, String[]> {

        @Override
        protected String[] doInBackground(Object... params) {
            String[] strs = new String[5];
            int nf = mThingsCounts.getCount(Thing.NOTE, Thing.FINISHED);
            int na = mThingsCounts.getCount(Thing.NOTE, ThingsCounts.ALL);
            strs[0] = nf + " / " + na;

            int rf = mThingsCounts.getCount(Thing.REMINDER, Thing.FINISHED);
            int ra = mThingsCounts.getCount(Thing.REMINDER, ThingsCounts.ALL);
            strs[1] = rf + " / " + ra;

            int hf = mThingsCounts.getCount(Thing.HABIT, Thing.FINISHED);
            int ha = mThingsCounts.getCount(Thing.HABIT, ThingsCounts.ALL);
            strs[2] = hf + " / " + ha;

            int gf = mThingsCounts.getCount(Thing.GOAL, Thing.FINISHED);
            int ga = mThingsCounts.getCount(Thing.GOAL, ThingsCounts.ALL);
            strs[3] = gf + " / " + ga;

            int af = nf + rf + hf + gf;
            int aa = na + ra + ha + ga;
            strs[4] = af + " / " + aa;
            return strs;
        }

        @Override
        protected void onPostExecute(String[] strings) {
            int[] iconRes = {
                    R.mipmap.drawer_note,
                    R.mipmap.drawer_reminder,
                    R.mipmap.drawer_habit,
                    R.mipmap.drawer_goal,
                    R.mipmap.drawer_all
            };
            int[] firstRes = {
                    R.string.note,
                    R.string.reminder,
                    R.string.habit,
                    R.string.goal,
                    R.string.all
            };
            RecyclerView rv = (RecyclerView) findViewById(R.id.rv_finished_created_statistic);
            rv.setAdapter(new StatisticAdapter(
                    StatisticActivity.this, iconRes, firstRes, null, strings));
            rv.setLayoutManager(new LinearLayoutManager(StatisticActivity.this));
        }
    }

    class NoteTask extends AsyncTask<Object, Object, String[]> {

        @Override
        protected String[] doInBackground(Object... params) {
            return getStrsForNoteRecord();
        }

        @Override
        protected void onPostExecute(String[] strings) {
            int[] iconRes = {
                    R.mipmap.ic_char_count,
                    R.mipmap.ic_image_count,
                    R.mipmap.ic_video_count,
                    R.mipmap.ic_audio_count
            };
            int[] firstRes = {
                    R.string.statistic_note_char_count,
                    R.string.statistic_note_image_count,
                    R.string.statistic_note_video_count,
                    R.string.statistic_note_audio_count
            };
            RecyclerView rv = (RecyclerView) findViewById(R.id.rv_note_record_statistic);
            rv.setAdapter(new StatisticAdapter(
                    StatisticActivity.this, iconRes, firstRes, null, strings));
            rv.setLayoutManager(new LinearLayoutManager(StatisticActivity.this));
        }
    }

    class ReminderTask extends AsyncTask<Object, Object, String[]> {

        @Override
        protected String[] doInBackground(Object... params) {
            return getStrsForReminderGoalRecord(true);
        }

        @Override
        protected void onPostExecute(String[] strings) {
            int[] iconRes = {
                    R.mipmap.drawer_finished,
                    R.mipmap.ic_average_notify_time,
                    R.mipmap.ic_average_finish_time,
                    R.mipmap.ic_finish_in_advance
            };
            int[] firstRes = {
                    R.string.statistic_reminder_completion_rate,
                    R.string.statistic_reminder_notify_time,
                    R.string.statistic_reminder_finish_time,
                    R.string.statistic_reminder_in_advance
            };
            RecyclerView rv = (RecyclerView) findViewById(R.id.rv_reminder_record_statistic);
            float[] textSizes;
            if (LocaleUtil.isChinese(mApplication)) {
                textSizes = null;
            } else {
                textSizes = new float[] { 14, 14, 14, 14 };
            }
            rv.setAdapter(new StatisticAdapter(StatisticActivity.this, iconRes, firstRes,
                    textSizes, getStrsForReminderGoalRecord(true)));
            rv.setLayoutManager(new LinearLayoutManager(StatisticActivity.this));
        }
    }

    class HabitTask extends AsyncTask<Object, Object, String[]> {

        @Override
        protected String[] doInBackground(Object... params) {
            return getStrsForHabitRecord();
        }

        @Override
        protected void onPostExecute(String[] strings) {
            int[] iconRes = {
                    R.mipmap.drawer_finished,
                    R.mipmap.ic_habit_finish_and_all,
                    R.mipmap.ic_habit_finish_rate,
                    R.mipmap.ic_longest_finish_times,
                    R.mipmap.ic_longest_pit
            };
            int[] firstRes = {
                    R.string.statistic_habit_developed_rate,
                    R.string.statistic_habit_finished_all,
                    R.string.statistic_habit_completion_rate,
                    R.string.statistic_habit_longest_finish_times,
                    R.string.statistic_habit_longest_pit
            };
            RecyclerView rv = (RecyclerView) findViewById(R.id.rv_habit_record_statistic);
            float[] textSizes;
            if (LocaleUtil.isChinese(mApplication)) {
                textSizes = new float[] { 16, 14, 16, 16, 16 };
            } else {
                textSizes = new float[] { 14, 14, 16, 14, 14 };
            }
            rv.setAdapter(new StatisticAdapter(StatisticActivity.this, iconRes, firstRes,
                    textSizes, strings));
            rv.setLayoutManager(new LinearLayoutManager(StatisticActivity.this));
        }
    }

    class GoalTask extends AsyncTask<Object, Object, String[]> {

        @Override
        protected String[] doInBackground(Object... params) {
            return getStrsForReminderGoalRecord(false);
        }

        @Override
        protected void onPostExecute(String[] strings) {
            int[] iconRes = {
                    R.mipmap.drawer_finished,
                    R.mipmap.ic_average_notify_time_goal,
                    R.mipmap.ic_average_finish_time_goal,
                    R.mipmap.ic_finish_in_advance
            };
            int[] firstRes = {
                    R.string.statistic_goal_completion_rate,
                    R.string.statistic_goal_notify_time,
                    R.string.statistic_reminder_finish_time,
                    R.string.statistic_reminder_in_advance
            };
            RecyclerView rv = (RecyclerView) findViewById(R.id.rv_goal_record_statistic);
            float[] textSizes;
            if (LocaleUtil.isChinese(mApplication)) {
                textSizes = null;
            } else {
                textSizes = new float[] { 14, 16, 14, 14 };
            }
            rv.setAdapter(new StatisticAdapter(StatisticActivity.this, iconRes, firstRes,
                    textSizes, strings));
            rv.setLayoutManager(new LinearLayoutManager(StatisticActivity.this));
        }
    }

    class ScreenshotTask extends AsyncTask<Object, Object, File> {

        @Override
        protected File doInBackground(Object... params) {
            // sleep this thread for 1s to make sure that scrollbar have been drawn completely.
            // As a result, we can get screenshot of mScrollView by view.draw(Canvas).
            // Otherwise, we may get Exception and crash because we draw something in worker thread.
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return getScreenShot();
        }

        @Override
        protected void onPostExecute(File file) {
            mLdf.dismiss();
            mSharedFiles.add(file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("image/jpeg");
            intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
            startActivity(Intent.createChooser(
                    intent, getString(R.string.share_statistic)));
        }
    }
}
