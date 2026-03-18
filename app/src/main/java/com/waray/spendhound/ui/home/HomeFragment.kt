package com.waray.spendhound.ui.home;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.waray.spendhound.CurrencyUtils;
import com.waray.spendhound.DeclareDatabase;
import com.waray.spendhound.LoginActivity;
import com.waray.spendhound.MainActivity;
import com.waray.spendhound.R;
import com.waray.spendhound.RecentTransaction;
import com.waray.spendhound.Transaction;
import com.waray.spendhound.databinding.FragmentHomeBinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class HomeFragment extends Fragment {

    private TextView day7TextView, day6TextView, day5TextView, day4TextView, day3TextView, day2TextView, day1TextView;
    private FragmentHomeBinding binding;
    private CardView cardViewProfile;
    public FirebaseAuth mAuth;

    // Weekly/Monthly Toggle and Navigation
    private TextView btnWeekly, btnMonthly, dateRangeText;
    private ImageButton btnPrevious, btnNext;
    private LinearLayout weeklyChartContainer;
    private LineChart monthlyLineChart;
    private boolean isWeeklyMode = true;
    private Calendar currentWeekStart = Calendar.getInstance();
    private Calendar currentMonth = Calendar.getInstance();

    private View loadingOverlay_home;
    private int pendingLoads = 0;


    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View view = binding.getRoot();

        loadingOverlay_home = view.findViewById(R.id.loadingOverlay_home);
        if (loadingOverlay_home != null) {
            loadingOverlay_home.setVisibility(View.VISIBLE);
        }

        day7TextView = view.findViewById(R.id.day7);
        day6TextView = view.findViewById(R.id.day6);
        day5TextView = view.findViewById(R.id.day5);
        day4TextView = view.findViewById(R.id.day4);
        day3TextView = view.findViewById(R.id.day3);
        day2TextView = view.findViewById(R.id.day2);
        day1TextView = view.findViewById(R.id.day1);
        cardViewProfile = view.findViewById(R.id.cardView_profile);
        mAuth = DeclareDatabase.getAuth();

        btnWeekly = view.findViewById(R.id.btnWeekly);
        btnMonthly = view.findViewById(R.id.btnMonthly);
        dateRangeText = view.findViewById(R.id.dateRangeText);
        btnPrevious = view.findViewById(R.id.btnPrevious);
        btnNext = view.findViewById(R.id.btnNext);
        weeklyChartContainer = view.findViewById(R.id.weeklyChartContainer);
        monthlyLineChart = view.findViewById(R.id.monthlyLineChart);

        initializeCurrentWeekStart();
        setupToggleListeners();
        setupNavigationListeners();
        updateDateRangeDisplay();
        callMainActivityMethod();

        setTextViews();
        AppCompatActivity activity = (AppCompatActivity) getActivity();
        if (activity != null && activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().hide();
        }

        return view;
    }

    private void callMainActivityMethod() {
        MainActivity mainActivity = (MainActivity) getActivity();
        if (mainActivity != null) {
            showLoading();
            mainActivity.getRecentTransaction(this::hideLoading);
            showLoading();
            mainActivity.getTotalMonthSpends(this::hideLoading);
            showLoading();
            mainActivity.getEverydaySpends(this::hideLoading);
        }
    }

    public void setTextViews() {
        setTextViewsForWeek();
    }

    public String getFormattedDay(Calendar calendar) {
        return new SimpleDateFormat("EEE", Locale.getDefault()).format(calendar.getTime());
    }


    private void initializeCurrentWeekStart() {
        currentWeekStart = Calendar.getInstance();
        currentWeekStart.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
        currentWeekStart.set(Calendar.HOUR_OF_DAY, 0);
        currentWeekStart.set(Calendar.MINUTE, 0);
        currentWeekStart.set(Calendar.SECOND, 0);
        currentWeekStart.set(Calendar.MILLISECOND, 0);
    }

    private void setupToggleListeners() {
        btnWeekly.setOnClickListener(v -> {
            if (!isWeeklyMode) {
                isWeeklyMode = true;
                updateToggleUI();
                updateDateRangeDisplay();
                showWeeklyChart();
                refreshWeeklyData();
            }
        });

        btnMonthly.setOnClickListener(v -> {
            if (isWeeklyMode) {
                isWeeklyMode = false;
                updateToggleUI();
                updateDateRangeDisplay();
                showMonthlyChart();
                loadMonthlyChartData();
            }
        });
    }

    private void updateToggleUI() {
        if (isWeeklyMode) {
            btnWeekly.setBackgroundResource(R.drawable.toggle_selected_background);
            btnWeekly.setTextColor(ContextCompat.getColor(requireContext(), R.color.darkBlue));
            btnMonthly.setBackgroundColor(Color.TRANSPARENT);
            btnMonthly.setTextColor(Color.parseColor("#adb5bd"));
        } else {
            btnMonthly.setBackgroundResource(R.drawable.toggle_selected_background);
            btnMonthly.setTextColor(ContextCompat.getColor(requireContext(), R.color.darkBlue));
            btnWeekly.setBackgroundColor(Color.TRANSPARENT);
            btnWeekly.setTextColor(Color.parseColor("#adb5bd"));
        }
    }

    private void setupNavigationListeners() {
        btnPrevious.setOnClickListener(v -> {
            if (isWeeklyMode) { currentWeekStart.add(Calendar.WEEK_OF_YEAR, -1); }
            else { currentMonth.add(Calendar.MONTH, -1); }
            updateDateRangeDisplay(); refreshData();
        });

        btnNext.setOnClickListener(v -> {
            if (isWeeklyMode) { currentWeekStart.add(Calendar.WEEK_OF_YEAR, 1); }
            else { currentMonth.add(Calendar.MONTH, 1); }
            updateDateRangeDisplay(); refreshData();
        });
    }

    private void updateDateRangeDisplay() {
        if (isWeeklyMode) {
            Calendar weekEnd = (Calendar) currentWeekStart.clone(); weekEnd.add(Calendar.DAY_OF_YEAR, 6);
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd", Locale.getDefault());
            SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy", Locale.getDefault());
            dateRangeText.setText(String.format("%s - %s, %s", dateFormat.format(currentWeekStart.getTime()), dateFormat.format(weekEnd.getTime()), yearFormat.format(weekEnd.getTime())));
        } else {
            dateRangeText.setText(new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(currentMonth.getTime()));
        }
    }

    private void refreshData() { if (isWeeklyMode) refreshWeeklyData(); else loadMonthlyChartData(); }

    private void refreshWeeklyData() {
        MainActivity mainActivity = (MainActivity) getActivity();
        if (mainActivity != null) {
            showLoading();
            mainActivity.getEverydaySpendsForWeek(currentWeekStart, this::hideLoading);
        }
        setTextViewsForWeek();
    }

    private void setTextViewsForWeek() {
        Calendar calendar = (Calendar) currentWeekStart.clone();
        Calendar today = Calendar.getInstance();
        TextView[] dayTextViews = {day7TextView, day6TextView, day5TextView, day4TextView, day3TextView, day2TextView, day1TextView};
        for (int i = 0; i < 7; i++) {
            if (dayTextViews[i] != null) {
                dayTextViews[i].setText(getFormattedDay(calendar));
                if (isSameDay(calendar, today)) {
                    dayTextViews[i].setTextColor(ContextCompat.getColor(requireContext(), R.color.yellow));
                    dayTextViews[i].setTypeface(null, android.graphics.Typeface.BOLD);
                } else {
                    dayTextViews[i].setTextColor(Color.WHITE);
                    dayTextViews[i].setTypeface(null, android.graphics.Typeface.NORMAL);
                }
            }
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }
    }

    private boolean isSameDay(Calendar cal1, Calendar cal2) { return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR); }
    private void showWeeklyChart() { weeklyChartContainer.setVisibility(View.VISIBLE); monthlyLineChart.setVisibility(View.GONE); }
    private void showMonthlyChart() { weeklyChartContainer.setVisibility(View.GONE); monthlyLineChart.setVisibility(View.VISIBLE); }

    private void loadMonthlyChartData() {
        MainActivity mainActivity = (MainActivity) getActivity();
        if (mainActivity == null) return;
        showLoading();
        String username = mainActivity.currentNickname;
        if (username == null || username.isEmpty()) { mainActivity.getCurrentNickname(nickname -> fetchMonthlyChartData(nickname, mainActivity)); }
        else { fetchMonthlyChartData(username, mainActivity); }
    }

    private void fetchMonthlyChartData(String username, MainActivity mainActivity) {
        DeclareDatabase.getDBRefTransaction().child(new SimpleDateFormat("MMMM-yyyy", Locale.getDefault()).format(currentMonth.getTime())).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                List<Entry> entries = new ArrayList<>(); List<String> labels = new ArrayList<>();
                int dayIndex = 0;
                for (DataSnapshot daySnapshot : dataSnapshot.getChildren()) {
                    int dailySpend = 0;
                    for (DataSnapshot timeSnapshot : daySnapshot.getChildren()) {
                        Transaction t = timeSnapshot.getValue(Transaction.class);
                        if (t != null && mainActivity.isUserInvolved(t, username)) dailySpend += t.getPaymentAmount();
                    }
                    entries.add(new Entry(dayIndex, dailySpend)); labels.add(daySnapshot.getKey()); dayIndex++;
                }
                if (entries.isEmpty()) { monthlyLineChart.clear(); monthlyLineChart.invalidate(); }
                else { setupLineChart(entries, labels); }
                hideLoading();
            }
            @Override public void onCancelled(@NonNull DatabaseError databaseError) { hideLoading(); }
        });
    }

    private void setupLineChart(List<Entry> entries, List<String> labels) {
        LineDataSet dataSet = new LineDataSet(entries, "Daily Spending");
        dataSet.setColor(Color.parseColor("#FFBA08")); dataSet.setValueTextColor(Color.WHITE); dataSet.setLineWidth(2f);
        dataSet.setCircleColor(Color.parseColor("#FFBA08")); dataSet.setCircleRadius(4f); dataSet.setDrawCircleHole(false);
        dataSet.setDrawValues(false); dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER); dataSet.setDrawFilled(true);
        dataSet.setFillColor(Color.parseColor("#FFBA08")); dataSet.setFillAlpha(50);
        monthlyLineChart.setData(new LineData(dataSet)); monthlyLineChart.getDescription().setEnabled(false);
        monthlyLineChart.setDrawGridBackground(false); monthlyLineChart.setDrawBorders(false); monthlyLineChart.getLegend().setEnabled(false);
        XAxis xAxis = monthlyLineChart.getXAxis(); xAxis.setPosition(XAxis.XAxisPosition.BOTTOM); xAxis.setTextColor(Color.WHITE);
        xAxis.setDrawGridLines(false); xAxis.setDrawAxisLine(false); xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setGranularity(1f); xAxis.setLabelRotationAngle(-45); xAxis.setTextSize(10f);
        YAxis leftAxis = monthlyLineChart.getAxisLeft(); leftAxis.setTextColor(Color.parseColor("#adb5bd"));
        leftAxis.setDrawGridLines(true); leftAxis.setGridColor(Color.parseColor("#3A3D4E"));
        leftAxis.setDrawAxisLine(false); leftAxis.setTextSize(10f);
        monthlyLineChart.getAxisRight().setEnabled(false); monthlyLineChart.setTouchEnabled(true);
        monthlyLineChart.setDragEnabled(true); monthlyLineChart.setScaleEnabled(true); monthlyLineChart.setPinchZoom(true);
        monthlyLineChart.animateX(500); monthlyLineChart.invalidate();
    }

    private void showLoading() { pendingLoads++; if (loadingOverlay_home != null) loadingOverlay_home.setVisibility(View.VISIBLE); }
    private void hideLoading() { pendingLoads = Math.max(0, pendingLoads - 1); if (pendingLoads == 0 && loadingOverlay_home != null) loadingOverlay_home.setVisibility(View.GONE); }
}
