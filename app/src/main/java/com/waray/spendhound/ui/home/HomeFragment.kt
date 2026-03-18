package com.waray.spendhound.ui.home

import com.google.firebase.auth.FirebaseAuth

class HomeFragment : androidx.fragment.app.Fragment() {
    private var day7TextView: TextView? = null
    private var day6TextView: TextView? = null
    private var day5TextView: TextView? = null
    private var day4TextView: TextView? = null
    private var day3TextView: TextView? = null
    private var day2TextView: TextView? = null
    private var day1TextView: TextView? = null
    private var binding: com.waray.spendhound.databinding.FragmentHomeBinding? = null
    private var cardViewProfile: CardView? = null
    var mAuth: FirebaseAuth? = null

    // Weekly/Monthly Toggle and Navigation
    private var btnWeekly: TextView? = null
    private var btnMonthly: TextView? = null
    private var dateRangeText: TextView? = null
    private var btnPrevious: ImageButton? = null
    private var btnNext: ImageButton? = null
    private var weeklyChartContainer: LinearLayout? = null
    private var monthlyLineChart: LineChart? = null
    private var isWeeklyMode = true
    private var currentWeekStart: java.util.Calendar = java.util.Calendar.getInstance()
    private val currentMonth: java.util.Calendar = java.util.Calendar.getInstance()

    private var loadingOverlay_home: android.view.View? = null
    private var pendingLoads = 0


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?, savedInstanceState: Bundle?
    ): android.view.View {
        binding =
            com.waray.spendhound.databinding.FragmentHomeBinding.inflate(inflater, container, false)
        val view: android.view.View = binding!!.getRoot()

        loadingOverlay_home = view.findViewById<android.view.View?>(R.id.loadingOverlay_home)
        if (loadingOverlay_home != null) {
            loadingOverlay_home!!.setVisibility(android.view.View.VISIBLE)
        }

        day7TextView = view.findViewById<TextView?>(R.id.day7)
        day6TextView = view.findViewById<TextView?>(R.id.day6)
        day5TextView = view.findViewById<TextView?>(R.id.day5)
        day4TextView = view.findViewById<TextView?>(R.id.day4)
        day3TextView = view.findViewById<TextView?>(R.id.day3)
        day2TextView = view.findViewById<TextView?>(R.id.day2)
        day1TextView = view.findViewById<TextView?>(R.id.day1)
        cardViewProfile = view.findViewById<CardView?>(R.id.cardView_profile)
        mAuth = DeclareDatabase.getAuth()

        btnWeekly = view.findViewById<TextView>(R.id.btnWeekly)
        btnMonthly = view.findViewById<TextView>(R.id.btnMonthly)
        dateRangeText = view.findViewById<TextView>(R.id.dateRangeText)
        btnPrevious = view.findViewById<ImageButton>(R.id.btnPrevious)
        btnNext = view.findViewById<ImageButton>(R.id.btnNext)
        weeklyChartContainer = view.findViewById<LinearLayout>(R.id.weeklyChartContainer)
        monthlyLineChart = view.findViewById<LineChart>(R.id.monthlyLineChart)

        initializeCurrentWeekStart()
        setupToggleListeners()
        setupNavigationListeners()
        updateDateRangeDisplay()
        callMainActivityMethod()

        setTextViews()
        val activity: AppCompatActivity? = getActivity() as AppCompatActivity?
        if (activity != null && activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().hide()
        }

        return view
    }

    private fun callMainActivityMethod() {
        val mainActivity: MainActivity? = getActivity() as MainActivity?
        if (mainActivity != null) {
            showLoading()
            mainActivity.getRecentTransaction(java.lang.Runnable { this.hideLoading() })
            showLoading()
            mainActivity.getTotalMonthSpends(java.lang.Runnable { this.hideLoading() })
            showLoading()
            mainActivity.getEverydaySpends(java.lang.Runnable { this.hideLoading() })
        }
    }

    fun setTextViews() {
        setTextViewsForWeek()
    }

    fun getFormattedDay(calendar: java.util.Calendar): kotlin.String {
        return java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault())
            .format(calendar.getTime())
    }


    private fun initializeCurrentWeekStart() {
        currentWeekStart = java.util.Calendar.getInstance()
        currentWeekStart.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.SUNDAY)
        currentWeekStart.set(java.util.Calendar.HOUR_OF_DAY, 0)
        currentWeekStart.set(java.util.Calendar.MINUTE, 0)
        currentWeekStart.set(java.util.Calendar.SECOND, 0)
        currentWeekStart.set(java.util.Calendar.MILLISECOND, 0)
    }

    private fun setupToggleListeners() {
        btnWeekly.setOnClickListener(android.view.View.OnClickListener { v: android.view.View? ->
            if (!isWeeklyMode) {
                isWeeklyMode = true
                updateToggleUI()
                updateDateRangeDisplay()
                showWeeklyChart()
                refreshWeeklyData()
            }
        })

        btnMonthly.setOnClickListener(android.view.View.OnClickListener { v: android.view.View? ->
            if (isWeeklyMode) {
                isWeeklyMode = false
                updateToggleUI()
                updateDateRangeDisplay()
                showMonthlyChart()
                loadMonthlyChartData()
            }
        })
    }

    private fun updateToggleUI() {
        if (isWeeklyMode) {
            btnWeekly.setBackgroundResource(R.drawable.toggle_selected_background)
            btnWeekly.setTextColor(ContextCompat.getColor(requireContext(), R.color.darkBlue))
            btnMonthly.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            btnMonthly.setTextColor(android.graphics.Color.parseColor("#adb5bd"))
        } else {
            btnMonthly.setBackgroundResource(R.drawable.toggle_selected_background)
            btnMonthly.setTextColor(ContextCompat.getColor(requireContext(), R.color.darkBlue))
            btnWeekly.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            btnWeekly.setTextColor(android.graphics.Color.parseColor("#adb5bd"))
        }
    }

    private fun setupNavigationListeners() {
        btnPrevious.setOnClickListener(android.view.View.OnClickListener { v: android.view.View? ->
            if (isWeeklyMode) {
                currentWeekStart.add(java.util.Calendar.WEEK_OF_YEAR, -1)
            } else {
                currentMonth.add(java.util.Calendar.MONTH, -1)
            }
            updateDateRangeDisplay()
            refreshData()
        })

        btnNext.setOnClickListener(android.view.View.OnClickListener { v: android.view.View? ->
            if (isWeeklyMode) {
                currentWeekStart.add(java.util.Calendar.WEEK_OF_YEAR, 1)
            } else {
                currentMonth.add(java.util.Calendar.MONTH, 1)
            }
            updateDateRangeDisplay()
            refreshData()
        })
    }

    private fun updateDateRangeDisplay() {
        if (isWeeklyMode) {
            val weekEnd = currentWeekStart.clone() as java.util.Calendar
            weekEnd.add(java.util.Calendar.DAY_OF_YEAR, 6)
            val dateFormat = java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault())
            val yearFormat = java.text.SimpleDateFormat("yyyy", java.util.Locale.getDefault())
            dateRangeText.setText(
                kotlin.String.format(
                    "%s - %s, %s",
                    dateFormat.format(currentWeekStart.getTime()),
                    dateFormat.format(weekEnd.getTime()),
                    yearFormat.format(weekEnd.getTime())
                )
            )
        } else {
            dateRangeText.setText(
                java.text.SimpleDateFormat(
                    "MMMM yyyy",
                    java.util.Locale.getDefault()
                ).format(currentMonth.getTime())
            )
        }
    }

    private fun refreshData() {
        if (isWeeklyMode) refreshWeeklyData() else loadMonthlyChartData()
    }

    private fun refreshWeeklyData() {
        val mainActivity: MainActivity? = getActivity() as MainActivity?
        if (mainActivity != null) {
            showLoading()
            mainActivity.getEverydaySpendsForWeek(
                currentWeekStart,
                java.lang.Runnable { this.hideLoading() })
        }
        setTextViewsForWeek()
    }

    private fun setTextViewsForWeek() {
        val calendar = currentWeekStart.clone() as java.util.Calendar
        val today = java.util.Calendar.getInstance()
        val dayTextViews: kotlin.Array<TextView?> = kotlin.arrayOf<TextView?>(
            day7TextView,
            day6TextView,
            day5TextView,
            day4TextView,
            day3TextView,
            day2TextView,
            day1TextView
        )
        for (i in 0..6) {
            if (dayTextViews[i] != null) {
                dayTextViews[i].setText(getFormattedDay(calendar))
                if (isSameDay(calendar, today)) {
                    dayTextViews[i].setTextColor(
                        ContextCompat.getColor(
                            requireContext(),
                            R.color.yellow
                        )
                    )
                    dayTextViews[i].setTypeface(null, android.graphics.Typeface.BOLD)
                } else {
                    dayTextViews[i].setTextColor(android.graphics.Color.WHITE)
                    dayTextViews[i].setTypeface(null, android.graphics.Typeface.NORMAL)
                }
            }
            calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
    }

    private fun isSameDay(cal1: java.util.Calendar, cal2: java.util.Calendar): kotlin.Boolean {
        return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) && cal1.get(
            java.util.Calendar.DAY_OF_YEAR
        ) == cal2.get(java.util.Calendar.DAY_OF_YEAR)
    }

    private fun showWeeklyChart() {
        weeklyChartContainer.setVisibility(android.view.View.VISIBLE)
        monthlyLineChart.setVisibility(android.view.View.GONE)
    }

    private fun showMonthlyChart() {
        weeklyChartContainer.setVisibility(android.view.View.GONE)
        monthlyLineChart.setVisibility(android.view.View.VISIBLE)
    }

    private fun loadMonthlyChartData() {
        val mainActivity: MainActivity? = getActivity() as MainActivity?
        if (mainActivity == null) return
        showLoading()
        val username: kotlin.String? = mainActivity.currentNickname
        if (username == null || username.isEmpty()) {
            mainActivity.getCurrentNickname(CurrentNicknameCallback { nickname: kotlin.String? ->
                fetchMonthlyChartData(
                    nickname,
                    mainActivity
                )
            })
        } else {
            fetchMonthlyChartData(username, mainActivity)
        }
    }

    private fun fetchMonthlyChartData(username: kotlin.String?, mainActivity: MainActivity) {
        DeclareDatabase.getDBRefTransaction().child(
            java.text.SimpleDateFormat("MMMM-yyyy", java.util.Locale.getDefault())
                .format(currentMonth.getTime())
        ).addListenerForSingleValueEvent(object : ValueEventListener() {
            public override fun onDataChange(dataSnapshot: DataSnapshot) {
                val entries: kotlin.collections.MutableList<com.github.mikephil.charting.data.Entry?> =
                    java.util.ArrayList<com.github.mikephil.charting.data.Entry?>()
                val labels: kotlin.collections.MutableList<kotlin.String?> =
                    java.util.ArrayList<kotlin.String?>()
                var dayIndex = 0
                for (daySnapshot in dataSnapshot.getChildren()) {
                    var dailySpend = 0
                    for (timeSnapshot in daySnapshot.getChildren()) {
                        val t: com.waray.spendhound.Transaction? =
                            timeSnapshot.getValue(com.waray.spendhound.Transaction::class.java)
                        if (t != null && mainActivity.isUserInvolved(t, username)) dailySpend =
                            (dailySpend + t.getPaymentAmount()).toInt()
                    }
                    entries.add(
                        com.github.mikephil.charting.data.Entry(
                            dayIndex.toFloat(),
                            dailySpend.toFloat()
                        )
                    )
                    labels.add(daySnapshot.getKey())
                    dayIndex++
                }
                if (entries.isEmpty()) {
                    monthlyLineChart.clear()
                    monthlyLineChart.invalidate()
                } else {
                    setupLineChart(entries, labels)
                }
                hideLoading()
            }

            public override fun onCancelled(databaseError: DatabaseError) {
                hideLoading()
            }
        })
    }

    private fun setupLineChart(
        entries: kotlin.collections.MutableList<com.github.mikephil.charting.data.Entry?>?,
        labels: kotlin.collections.MutableList<kotlin.String?>?
    ) {
        val dataSet: LineDataSet = LineDataSet(entries, "Daily Spending")
        dataSet.setColor(android.graphics.Color.parseColor("#FFBA08"))
        dataSet.setValueTextColor(android.graphics.Color.WHITE)
        dataSet.setLineWidth(2f)
        dataSet.setCircleColor(android.graphics.Color.parseColor("#FFBA08"))
        dataSet.setCircleRadius(4f)
        dataSet.setDrawCircleHole(false)
        dataSet.setDrawValues(false)
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER)
        dataSet.setDrawFilled(true)
        dataSet.setFillColor(android.graphics.Color.parseColor("#FFBA08"))
        dataSet.setFillAlpha(50)
        monthlyLineChart.setData(LineData(dataSet))
        monthlyLineChart.getDescription().setEnabled(false)
        monthlyLineChart.setDrawGridBackground(false)
        monthlyLineChart.setDrawBorders(false)
        monthlyLineChart.getLegend().setEnabled(false)
        val xAxis: XAxis = monthlyLineChart.getXAxis()
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM)
        xAxis.setTextColor(android.graphics.Color.WHITE)
        xAxis.setDrawGridLines(false)
        xAxis.setDrawAxisLine(false)
        xAxis.setValueFormatter(IndexAxisValueFormatter(labels))
        xAxis.setGranularity(1f)
        xAxis.setLabelRotationAngle(-45f)
        xAxis.setTextSize(10f)
        val leftAxis: YAxis = monthlyLineChart.getAxisLeft()
        leftAxis.setTextColor(android.graphics.Color.parseColor("#adb5bd"))
        leftAxis.setDrawGridLines(true)
        leftAxis.setGridColor(android.graphics.Color.parseColor("#3A3D4E"))
        leftAxis.setDrawAxisLine(false)
        leftAxis.setTextSize(10f)
        monthlyLineChart.getAxisRight().setEnabled(false)
        monthlyLineChart.setTouchEnabled(true)
        monthlyLineChart.setDragEnabled(true)
        monthlyLineChart.setScaleEnabled(true)
        monthlyLineChart.setPinchZoom(true)
        monthlyLineChart.animateX(500)
        monthlyLineChart.invalidate()
    }

    private fun showLoading() {
        pendingLoads++
        if (loadingOverlay_home != null) loadingOverlay_home!!.setVisibility(android.view.View.VISIBLE)
    }

    private fun hideLoading() {
        pendingLoads = kotlin.math.max(0, pendingLoads - 1)
        if (pendingLoads == 0 && loadingOverlay_home != null) loadingOverlay_home!!.setVisibility(
            android.view.View.GONE
        )
    }
}
