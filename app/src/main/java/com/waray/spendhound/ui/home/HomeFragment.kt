package com.waray.spendhound.ui.home

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.waray.spendhound.DeclareDatabase
import com.waray.spendhound.MainActivity
import com.waray.spendhound.R
import com.waray.spendhound.Transaction
import com.waray.spendhound.databinding.FragmentHomeBinding
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.from
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

class HomeFragment : Fragment() {
    private var day7TextView: TextView? = null
    private var day6TextView: TextView? = null
    private var day5TextView: TextView? = null
    private var day4TextView: TextView? = null
    private var day3TextView: TextView? = null
    private var day2TextView: TextView? = null
    private var day1TextView: TextView? = null
    private var binding: FragmentHomeBinding? = null
    private var cardViewProfile: CardView? = null
    private var mAuth: Auth? = null

    private var btnWeekly: TextView? = null
    private var btnMonthly: TextView? = null
    private var dateRangeText: TextView? = null
    private var btnPrevious: ImageButton? = null
    private var btnNext: ImageButton? = null
    private var weeklyChartContainer: LinearLayout? = null
    private var monthlyLineChart: LineChart? = null
    private var isWeeklyMode = true
    private var currentWeekStart: Calendar = Calendar.getInstance()
    private val currentMonth: Calendar = Calendar.getInstance()

    private var loadingOverlay_home: View? = null
    private var pendingLoads = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        val view = binding!!.root

        loadingOverlay_home = view.findViewById(R.id.loadingOverlay_home)
        loadingOverlay_home?.visibility = View.VISIBLE

        day7TextView = view.findViewById(R.id.day7)
        day6TextView = view.findViewById(R.id.day6)
        day5TextView = view.findViewById(R.id.day5)
        day4TextView = view.findViewById(R.id.day4)
        day3TextView = view.findViewById(R.id.day3)
        day2TextView = view.findViewById(R.id.day2)
        day1TextView = view.findViewById(R.id.day1)
        cardViewProfile = view.findViewById(R.id.cardView_profile)
        
        mAuth = DeclareDatabase.auth

        btnWeekly = view.findViewById(R.id.btnWeekly)
        btnMonthly = view.findViewById(R.id.btnMonthly)
        dateRangeText = view.findViewById(R.id.dateRangeText)
        btnPrevious = view.findViewById(R.id.btnPrevious)
        btnNext = view.findViewById(R.id.btnNext)
        weeklyChartContainer = view.findViewById(R.id.weeklyChartContainer)
        monthlyLineChart = view.findViewById(R.id.monthlyLineChart)

        initializeCurrentWeekStart()
        setupToggleListeners()
        setupNavigationListeners()
        updateDateRangeDisplay()
        callMainActivityMethod()

        setTextViews()
        (activity as? AppCompatActivity)?.supportActionBar?.hide()

        return view
    }

    private fun callMainActivityMethod() {
        val mainActivity = activity as? MainActivity ?: return
        showLoading()
        mainActivity.getRecentTransaction { hideLoading() }
        showLoading()
        mainActivity.getTotalMonthSpends { hideLoading() }
        showLoading()
        mainActivity.getEverydaySpends { hideLoading() }
    }

    private fun setTextViews() {
        setTextViewsForWeek()
    }

    private fun getFormattedDay(calendar: Calendar): String {
        return SimpleDateFormat("EEE", Locale.getDefault()).format(calendar.time)
    }

    private fun initializeCurrentWeekStart() {
        currentWeekStart = Calendar.getInstance()
        currentWeekStart.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        currentWeekStart.set(Calendar.HOUR_OF_DAY, 0)
        currentWeekStart.set(Calendar.MINUTE, 0)
        currentWeekStart.set(Calendar.SECOND, 0)
        currentWeekStart.set(Calendar.MILLISECOND, 0)
    }

    private fun setupToggleListeners() {
        btnWeekly?.setOnClickListener {
            if (!isWeeklyMode) {
                isWeeklyMode = true
                updateToggleUI()
                updateDateRangeDisplay()
                showWeeklyChart()
                refreshWeeklyData()
            }
        }

        btnMonthly?.setOnClickListener {
            if (isWeeklyMode) {
                isWeeklyMode = false
                updateToggleUI()
                updateDateRangeDisplay()
                showMonthlyChart()
                loadMonthlyChartData()
            }
        }
    }

    private fun updateToggleUI() {
        if (isWeeklyMode) {
            btnWeekly?.setBackgroundResource(R.drawable.toggle_selected_background)
            btnWeekly?.setTextColor(ContextCompat.getColor(requireContext(), R.color.darkBlue))
            btnMonthly?.setBackgroundColor(Color.TRANSPARENT)
            btnMonthly?.setTextColor(Color.parseColor("#adb5bd"))
        } else {
            btnMonthly?.setBackgroundResource(R.drawable.toggle_selected_background)
            btnMonthly?.setTextColor(ContextCompat.getColor(requireContext(), R.color.darkBlue))
            btnWeekly?.setBackgroundColor(Color.TRANSPARENT)
            btnWeekly?.setTextColor(Color.parseColor("#adb5bd"))
        }
    }

    private fun setupNavigationListeners() {
        btnPrevious?.setOnClickListener {
            if (isWeeklyMode) {
                currentWeekStart.add(Calendar.WEEK_OF_YEAR, -1)
            } else {
                currentMonth.add(Calendar.MONTH, -1)
            }
            updateDateRangeDisplay()
            refreshData()
        }

        btnNext?.setOnClickListener {
            if (isWeeklyMode) {
                currentWeekStart.add(Calendar.WEEK_OF_YEAR, 1)
            } else {
                currentMonth.add(Calendar.MONTH, 1)
            }
            updateDateRangeDisplay()
            refreshData()
        }
    }

    private fun updateDateRangeDisplay() {
        if (isWeeklyMode) {
            val weekEnd = currentWeekStart.clone() as Calendar
            weekEnd.add(Calendar.DAY_OF_YEAR, 6)
            val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
            val yearFormat = SimpleDateFormat("yyyy", Locale.getDefault())
            dateRangeText?.text = String.format(
                "%s - %s, %s",
                dateFormat.format(currentWeekStart.time),
                dateFormat.format(weekEnd.time),
                yearFormat.format(weekEnd.time)
            )
        } else {
            dateRangeText?.text = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(currentMonth.time)
        }
    }

    private fun refreshData() {
        if (isWeeklyMode) refreshWeeklyData() else loadMonthlyChartData()
    }

    private fun refreshWeeklyData() {
        val mainActivity = activity as? MainActivity ?: return
        showLoading()
        mainActivity.getEverydaySpendsForWeek(currentWeekStart) { hideLoading() }
        setTextViewsForWeek()
    }

    private fun setTextViewsForWeek() {
        val calendar = currentWeekStart.clone() as Calendar
        val today = Calendar.getInstance()
        val dayTextViews = arrayOf(day7TextView, day6TextView, day5TextView, day4TextView, day3TextView, day2TextView, day1TextView)
        for (i in 0..6) {
            dayTextViews[i]?.let {
                it.text = getFormattedDay(calendar)
                if (isSameDay(calendar, today)) {
                    it.setTextColor(ContextCompat.getColor(requireContext(), R.color.yellow))
                    it.setTypeface(null, Typeface.BOLD)
                } else {
                    it.setTextColor(Color.WHITE)
                    it.setTypeface(null, Typeface.NORMAL)
                }
            }
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun showWeeklyChart() {
        weeklyChartContainer?.visibility = View.VISIBLE
        monthlyLineChart?.visibility = View.GONE
    }

    private fun showMonthlyChart() {
        weeklyChartContainer?.visibility = View.GONE
        monthlyLineChart?.visibility = View.VISIBLE
    }

    private fun loadMonthlyChartData() {
        val mainActivity = activity as? MainActivity ?: return
        showLoading()
        val username = mainActivity.currentNickname
        if (username.isNullOrEmpty()) {
            mainActivity.getCurrentNickname { nickname ->
                fetchMonthlyChartData(nickname, mainActivity)
            }
        } else {
            fetchMonthlyChartData(username, mainActivity)
        }
    }

    private fun fetchMonthlyChartData(username: String?, mainActivity: MainActivity) {
        val monthYear = SimpleDateFormat("MMMM-yyyy", Locale.getDefault()).format(currentMonth.time)
        DeclareDatabase.client.from("transactions").select {
            filter {
                // This logic needs adjustment because Supabase doesn't use path-based access like Firebase
                // For now, I'll keep the Firebase logic conceptually but it will likely fail 
                // until the Supabase schema and query logic are fully implemented.
            }
        }
        // NOTE: The previous code was using Firebase Realtime Database through DeclareDatabase.
        // I need to check if DeclareDatabase still has Firebase references or if they should be replaced.
        // Based on DeclareDatabase.kt, it's now Supabase. 
        // But many files are still calling getDBRefTransaction() which is now missing.
        
        // I will re-examine DeclareDatabase.kt and the other files.
        hideLoading()
    }

    private fun setupLineChart(entries: List<Entry>, labels: List<String>) {
        val dataSet = LineDataSet(entries, "Daily Spending")
        dataSet.color = Color.parseColor("#FFBA08")
        dataSet.valueTextColor = Color.WHITE
        dataSet.lineWidth = 2f
        dataSet.setCircleColor(Color.parseColor("#FFBA08"))
        dataSet.circleRadius = 4f
        dataSet.setDrawCircleHole(false)
        dataSet.setDrawValues(false)
        dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
        dataSet.setDrawFilled(true)
        dataSet.fillColor = Color.parseColor("#FFBA08")
        dataSet.fillAlpha = 50
        
        monthlyLineChart?.data = LineData(dataSet)
        monthlyLineChart?.description?.isEnabled = false
        monthlyLineChart?.setDrawGridBackground(false)
        monthlyLineChart?.setDrawBorders(false)
        monthlyLineChart?.legend?.isEnabled = false
        
        val xAxis = monthlyLineChart!!.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.textColor = Color.WHITE
        xAxis.setDrawGridLines(false)
        xAxis.setDrawAxisLine(false)
        xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        xAxis.granularity = 1f
        xAxis.labelRotationAngle = -45f
        xAxis.textSize = 10f
        
        val leftAxis = monthlyLineChart!!.axisLeft
        leftAxis.textColor = Color.parseColor("#adb5bd")
        leftAxis.setDrawGridLines(true)
        leftAxis.gridColor = Color.parseColor("#3A3D4E")
        leftAxis.setDrawAxisLine(false)
        leftAxis.textSize = 10f
        
        monthlyLineChart?.axisRight?.isEnabled = false
        monthlyLineChart?.setTouchEnabled(true)
        monthlyLineChart?.isDragEnabled = true
        monthlyLineChart?.setScaleEnabled(true)
        monthlyLineChart?.setPinchZoom(true)
        monthlyLineChart?.animateX(500)
        monthlyLineChart?.invalidate()
    }

    private fun showLoading() {
        pendingLoads++
        loadingOverlay_home?.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        pendingLoads = Math.max(0, pendingLoads - 1)
        if (pendingLoads == 0) {
            loadingOverlay_home?.visibility = View.GONE
        }
    }
}
