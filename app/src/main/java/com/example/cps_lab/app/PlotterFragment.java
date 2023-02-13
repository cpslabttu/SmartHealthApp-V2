package com.example.cps_lab.app;

import android.app.AlertDialog;
import android.bluetooth.BluetoothGatt;
import android.content.Context;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.example.cps_lab.R;
import com.example.cps_lab.ble.BleUtils;
import com.example.cps_lab.ble.central.BlePeripheral;
import com.example.cps_lab.ble.central.BlePeripheralUart;
import com.example.cps_lab.ble.central.BleScanner;
import com.example.cps_lab.ble.central.UartDataManager;
import com.example.cps_lab.style.UartStyle;
import com.example.cps_lab.utils.DialogUtils;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.opencsv.CSVWriter;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;

import java.io.FileWriter;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PlotterFragment extends ConnectedPeripheralFragment implements UartDataManager.UartDataManagerListener {
    // Log
    private final static String TAG = PlotterFragment.class.getSimpleName();

    // Config
    private final static int xMaxEntriesMin = 6;
    private final static int xMaxEntriesMax = 100;

    // UI
    private SeekBar xMaxEntriesSeekBar;
    private LineChart mChart;
    private EditText heartRateEditText;
    private EditText avgHeartRateEditText;

    // Data
    private UartDataManager mUartDataManager;
    private long mOriginTimestamp;
    private List<BlePeripheralUart> mBlePeripheralsUart = new ArrayList<>();
    private boolean mIsAutoScrollEnabled = true;
    private int mVisibleInterval = 20;        // in seconds
    private Map<String, DashPathEffect> mLineDashPathEffectForPeripheral = new HashMap<>();
    private Map<String, List<LineDataSet>> mDataSetsForPeripheral = new HashMap<>();
    private LineDataSet mLastDataSetModified;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private ArrayList<Double> timerData = new ArrayList<>();
    private int counter = 0;
    private Button csvButton;

    private List<String[]> heatRateData = new ArrayList<String[]>();

    // region Fragment Lifecycle
    public static PlotterFragment newInstance(@Nullable String singlePeripheralIdentifier) {
        PlotterFragment fragment = new PlotterFragment();
        fragment.setArguments(createFragmentArgs(singlePeripheralIdentifier));
        return fragment;
    }

    public PlotterFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Retain this fragment across configuration changes
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_plotter, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Update ActionBar
        setActionBarTitle(R.string.plotter_tab_title);

        // UI
        mChart = view.findViewById(R.id.chart);
        heartRateEditText= view.findViewById(R.id.heartBeatRate);
        avgHeartRateEditText= view.findViewById(R.id.avgheartBeatRate);
        csvButton = view.findViewById(R.id.csv_button);
        csvButton.setVisibility(View.GONE);
        WeakReference<PlotterFragment> weakThis = new WeakReference<>(this);
        SwitchCompat autoscrollSwitch = view.findViewById(R.id.autoscrollSwitch);
        autoscrollSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                PlotterFragment fragment = weakThis.get();          // Fix detected memory leak
                if (fragment != null) {
                    fragment.mIsAutoScrollEnabled = isChecked;
                    fragment.mChart.setDragEnabled(!isChecked);
                    fragment.notifyDataSetChanged();
                }
            }
        });
        xMaxEntriesSeekBar = view.findViewById(R.id.xMaxEntriesSeekBar);
        xMaxEntriesSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    final float factor = progress / 100.f;
                    mVisibleInterval = Math.round((xMaxEntriesMax - xMaxEntriesMin) * factor + xMaxEntriesMin);
                    notifyDataSetChanged();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        autoscrollSwitch.setChecked(mIsAutoScrollEnabled);
        mChart.setDragEnabled(!mIsAutoScrollEnabled);
        setXMaxEntriesValue(mVisibleInterval);

        // Setup
        Context context = getContext();
        if (context != null) {
            mUartDataManager = new UartDataManager(context, this, true);
            mOriginTimestamp = System.currentTimeMillis();

            setupChart();
            setupUart();
        }

        csvButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                CSVWriter writer = null;
                try {
                    writer = new CSVWriter(new FileWriter(csv));
                    writer.writeAll(heatRateData); // data is adding to csv

                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public void onDestroy() {
        if (mUartDataManager != null) {
            Context context = getContext();
            if (context != null) {
                mUartDataManager.setEnabled(context, false);
            }
        }

        if (mBlePeripheralsUart != null) {
            for (BlePeripheralUart blePeripheralUart : mBlePeripheralsUart) {
                blePeripheralUart.uartDisable();
            }
            mBlePeripheralsUart.clear();
            mBlePeripheralsUart = null;
        }

        super.onDestroy();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_plotter, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        FragmentActivity activity = getActivity();

        switch (item.getItemId()) {
            case R.id.action_help:
                if (activity != null) {
                    FragmentManager fragmentManager = activity.getSupportFragmentManager();
                    if (fragmentManager != null) {
                        CommonHelpFragment helpFragment = CommonHelpFragment.newInstance(getString(R.string.plotter_help_title), getString(R.string.plotter_help_text));
                        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction()
                                .replace(R.id.contentLayout, helpFragment, "Help");
                        fragmentTransaction.addToBackStack(null);
                        fragmentTransaction.commit();
                    }
                }
                return true;


            case R.id.action_csv:
                CSVWriter writer = null;
                try {
                    writer = new CSVWriter(new FileWriter(csv));
                    writer.writeAll(heatRateData); // data is adding to csv

                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }
    // endregion


    // region Uart

    private boolean isInMultiUartMode() {
        return mBlePeripheral == null;
    }

    private void setupUart() {
        // Line dashes assigned to peripherals
        final DashPathEffect[] dashPathEffects = UartStyle.defaultDashPathEffects();

        // Enable uart
        if (isInMultiUartMode()) {
            mLineDashPathEffectForPeripheral.clear();   // Reset line dashes assigned to peripherals
            List<BlePeripheral> connectedPeripherals = BleScanner.getInstance().getConnectedPeripherals();
            for (int i = 0; i < connectedPeripherals.size(); i++) {
                BlePeripheral blePeripheral = connectedPeripherals.get(i);
                mLineDashPathEffectForPeripheral.put(blePeripheral.getIdentifier(), dashPathEffects[i % dashPathEffects.length]);

                if (!BlePeripheralUart.isUartInitialized(blePeripheral, mBlePeripheralsUart)) {
                    BlePeripheralUart blePeripheralUart = new BlePeripheralUart(blePeripheral);
                    mBlePeripheralsUart.add(blePeripheralUart);
                    blePeripheralUart.uartEnable(mUartDataManager, status -> {

                        String peripheralName = blePeripheral.getName();
                        if (peripheralName == null) {
                            peripheralName = blePeripheral.getIdentifier();
                        }

                        String finalPeripheralName = peripheralName;
                        mMainHandler.post(() -> {
                            if (status == BluetoothGatt.GATT_SUCCESS) {
                                // Done
                                Log.d(TAG, "Uart enabled for: " + finalPeripheralName);
                            } else {
                                //WeakReference<BlePeripheralUart> weakBlePeripheralUart = new WeakReference<>(blePeripheralUart);
                                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                                AlertDialog dialog = builder.setMessage(String.format(getString(R.string.uart_error_multipleperiperipheralinit_format), finalPeripheralName))
                                        .setPositiveButton(android.R.string.ok, (dialogInterface, which) -> {
                                        /*
                                            BlePeripheralUart strongBlePeripheralUart = weakBlePeripheralUart.get();
                                        if (strongBlePeripheralUart != null) {
                                            strongBlePeripheralUart.disconnect();
                                        }*/
                                        })
                                        .show();
                                DialogUtils.keepDialogOnOrientationChanges(dialog);
                            }
                        });

                    });
                }
            }

        } else {       //  Single peripheral mode
            if (!BlePeripheralUart.isUartInitialized(mBlePeripheral, mBlePeripheralsUart)) { // If was not previously setup (i.e. orientation change)
                mLineDashPathEffectForPeripheral.clear();   // Reset line dashes assigned to peripherals
                mLineDashPathEffectForPeripheral.put(mBlePeripheral.getIdentifier(), dashPathEffects[0]);
                BlePeripheralUart blePeripheralUart = new BlePeripheralUart(mBlePeripheral);
                mBlePeripheralsUart.add(blePeripheralUart);
                blePeripheralUart.uartEnable(mUartDataManager, status -> mMainHandler.post(() -> {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        // Done
                        Log.d(TAG, "Uart enabled");
                    } else {
                        Context context = getContext();
                        if (context != null) {
                            WeakReference<BlePeripheralUart> weakBlePeripheralUart = new WeakReference<>(blePeripheralUart);
                            AlertDialog.Builder builder = new AlertDialog.Builder(context);
                            AlertDialog dialog = builder.setMessage(R.string.uart_error_peripheralinit)
                                    .setPositiveButton(android.R.string.ok, (dialogInterface, which) -> {
                                        BlePeripheralUart strongBlePeripheralUart = weakBlePeripheralUart.get();
                                        if (strongBlePeripheralUart != null) {
                                            strongBlePeripheralUart.disconnect();
                                        }
                                    })
                                    .show();
                            DialogUtils.keepDialogOnOrientationChanges(dialog);
                        }
                    }
                }));
            }
        }
    }

    // endregion

    // region Line Chart
    private void setupChart() {

        mChart.getDescription().setEnabled(false);
        mChart.getXAxis().setGranularityEnabled(true);
        mChart.getXAxis().setGranularity(5);

        mChart.setExtraOffsets(10, 10, 10, 0);
        mChart.getLegend().setEnabled(false);

        mChart.setNoDataTextColor(Color.BLACK);
        mChart.setNoDataText(getString(R.string.plotter_nodata));
    }

    private void setXMaxEntriesValue(int value) {
        final float percent = Math.max(0, (value - xMaxEntriesMin)) / (float) (xMaxEntriesMax - xMaxEntriesMin);
        final int progress = Math.round(percent * xMaxEntriesSeekBar.getMax());
        xMaxEntriesSeekBar.setProgress(progress);
    }

    private void addEntry(@NonNull String peripheralIdentifier, int index, float value, float timestamp) {
        Entry entry = new Entry(timestamp, value);

        boolean dataSetExists = false;
        List<LineDataSet> dataSets = mDataSetsForPeripheral.get(peripheralIdentifier);
        if (dataSets != null) {
            if (index < dataSets.size()) {
                // Add entry to existing dataset
                LineDataSet dataSet = dataSets.get(index);
                dataSet.addEntry(entry);
                dataSetExists = true;
            }
        }

        if (!dataSetExists) {
            appendDataset(peripheralIdentifier, entry, index);

            List<ILineDataSet> allDataSets = new ArrayList<>();
            for (List<LineDataSet> dataSetLists : mDataSetsForPeripheral.values()) {
                allDataSets.addAll(dataSetLists);
            }
            final LineData lineData = new LineData(allDataSets);
            mChart.setData(lineData);
        }

        List<LineDataSet> dataSets2 = mDataSetsForPeripheral.get(peripheralIdentifier);
        if (dataSets2 != null && index < dataSets2.size()) {
            mLastDataSetModified = dataSets2.get(index);
        }
    }

    private void notifyDataSetChanged() {
        if (mChart.getData() != null) {
            mChart.getData().notifyDataChanged();
        }
        mChart.notifyDataSetChanged();
        mChart.invalidate();
        mChart.setVisibleXRangeMaximum(mVisibleInterval);
        mChart.setVisibleXRangeMinimum(mVisibleInterval);

        if (mLastDataSetModified != null && mIsAutoScrollEnabled) {
            final List<Entry> values = mLastDataSetModified.getValues();

            float x = 0;
            if (values != null && values.size() > 0) {
                Entry value = values.get(values.size() - 1);
                if (value != null) {
                    x = value.getX();
                }
            }

            final float xOffset = x - (mVisibleInterval - 1);
            mChart.moveViewToX(xOffset);
        }
    }

    private void appendDataset(@NonNull String peripheralIdentifier, @NonNull Entry entry, int index) {
        LineDataSet dataSet = new LineDataSet(null, "Values[" + peripheralIdentifier + ":" + index + "]");
        dataSet.addEntry(entry);
        dataSet.addEntry(entry);

        dataSet.setDrawCircles(false);
        dataSet.setDrawValues(false);
        dataSet.setLineWidth(2);
        final int[] colors = UartStyle.defaultColors();
        final int color = colors[index % colors.length];
        dataSet.setColor(color);
        final DashPathEffect dashPatternEffect = mLineDashPathEffectForPeripheral.get(peripheralIdentifier);
        dataSet.setFormLineDashEffect(dashPatternEffect);

        List<LineDataSet> previousDataSets = mDataSetsForPeripheral.get(peripheralIdentifier);
        if (previousDataSets != null) {
            previousDataSets.add(dataSet);
        } else {
            List<LineDataSet> dataSets = new ArrayList<>();
            dataSets.add(dataSet);
            mDataSetsForPeripheral.put(peripheralIdentifier, dataSets);
        }
    }

    // endregion

    // region UartDataManagerListener
    private static final byte kLineSeparator = 10;
    private int count = 0;
    private double sumofAvgHeartBeatRate = 0;
    private int heartRateCount = 0;
    private double avgHeartBeatRate = 0;
    private List<Double> heartRates = new ArrayList<>();
    private  String csv = (Environment.getExternalStorageDirectory().getAbsolutePath() + "/HeartRate.csv");

    @Override
    public void onUartRx(@NonNull byte[] data, @Nullable String peripheralIdentifier) {
        /*
        Log.d(TAG, "uart rx read (hex): " + BleUtils.bytesToHex2(data));
        try {
            Log.d(TAG, "uart rx read (utf8): " + new String(data, "UTF-8"));
        } catch (UnsupportedEncodingException ignored) {
        }*/

        // Find last separator
        boolean found = false;
        int i = data.length - 1;
        while (i >= 0 && !found) {
            if (data[i] > -99999999) {
                found = true;
            } else {
                i--;
            }
        }
        final int lastSeparator = i + 1;

        //
        if (found) {
            final byte[] subData = Arrays.copyOfRange(data, 0, lastSeparator);
            final float currentTimestamp = (System.currentTimeMillis() - mOriginTimestamp) / 1000.f;
            String dataString = BleUtils.bytesToHex2(subData);

            String[] strings = dataString.split(" ");
            String[] dataStrings= new String[strings.length/2];
            int l= 0;
            for(int j=0;j<strings.length/2;j++){
                dataStrings[j]= strings[l+1].charAt(1) + strings[l];
                dataStrings[j]= String.valueOf(Integer.parseInt(dataStrings[j],16));
                l+=2;
            }

            //Python
            /* Python python= Python.getInstance();
            PyObject pyObject= python.getModule("Pan_Tompkins");
            PyObject args= PyObject.fromJava(records);
            PyObject object= pyObject.callAttr("findPeaksForECG", records);
            String objectToString= object.toString();*/

            // Peak Detection from java
            DecimalFormat df = new DecimalFormat("#0.000");
            ArrayList<Double> doubleData= new ArrayList<Double>();
            for(String lineString : dataStrings){
                doubleData.add(Double.parseDouble(lineString));
            }

            int lag = 30;
            double threshold = 5;
            double influence = 0;

            HashMap<String, List> resultsMap = analyzeDataForSignals(doubleData, lag, threshold, influence);

            List<Integer> signalsList = resultsMap.get("signals");
            for (int signal : signalsList) {
                if(signal > 0){
                    count++;
                }
            }
            List<Double> filteredDataList = resultsMap.get("filteredData");

            /* Heart Rate Calculation */
            counter++;
            timerData.addAll(filteredDataList);
            if(heatRateData.size() == 0)
                heatRateData.add(new String[]{"Heart Rate", "Avg. Heart Rate"});
            if(counter % 10 == 0) {
                List<Integer> rPeaks = RPeakDetector.detectRPeaks(timerData);
                double heartRate = calculateHeartRate(rPeaks, 10);
                if (heartRate > 60 && heartRate < 140) {
                    heartRateEditText.setTextIsSelectable(true);
                    heartRateEditText.setMovementMethod(LinkMovementMethod.getInstance());
                    heartRateEditText.setText(String.valueOf((int) heartRate));

                    heartRates.add(heartRate);
                    System.out.println("HR C " + heartRates.size() + " List " + heartRates);
                    if (heartRates.size() <= 30){
                        heatRateData.add(new String[]{String.valueOf((int) heartRate), "0"});
                    }
                    else {
                        for(int hrC=0;hrC<30;hrC++){
                            sumofAvgHeartBeatRate += heartRates.get(hrC);
                        }
                        avgHeartBeatRate = sumofAvgHeartBeatRate /30;
                        avgHeartRateEditText.setText(String.valueOf((int) avgHeartBeatRate));
                        heatRateData.add(new String[]{String.valueOf((int) heartRate), String.valueOf((int) avgHeartBeatRate)});
                        heartRates = shiftLeft(heartRates);
                        sumofAvgHeartBeatRate = 0;
                    }

                }
                timerData = new ArrayList<>();
            }

            for (Double filteredData : filteredDataList) {
                String lineString= Double.toString(filteredData);
                final String[] valuesStrings = lineString.split("[,; \t]");
                int j = 0;
                for (String valueString : valuesStrings) {
                    boolean isValid = true;
                    float value = 0;
                    if (valueString != null) {
                        try {
                            value = Float.parseFloat(valueString);
                        } catch (NumberFormatException ignored) {
                            isValid = false;
                        }
                    } else {
                        isValid = false;
                    }

                    if (isValid && peripheralIdentifier != null) {
                        //Log.d(TAG, "value " + j + ": (" + currentTimestamp + ", " + value + ")");
                        //Log.d(TAG, "value " + j + ": " + value);
                        addEntry(peripheralIdentifier, j, value, currentTimestamp);
                        j++;
                    }
                }

                mMainHandler.post(this::notifyDataSetChanged);
            }
            //timerSetUp();
        }

        mUartDataManager.removeRxCacheFirst(lastSeparator, peripheralIdentifier);
    }

    public HashMap<String, List> analyzeDataForSignals(List<Double> data, int lag, Double threshold, Double influence) {

        // init stats instance
        SummaryStatistics stats = new SummaryStatistics();

        // the results (peaks, 1 or -1) of our algorithm
        List<Integer> signals = new ArrayList<Integer>(Collections.nCopies(data.size(), 0));

        // filter out the signals (peaks) from our original list (using influence arg)
        List<Double> filteredData = new ArrayList<Double>(data);

        // the current average of the rolling window
        List<Double> avgFilter = new ArrayList<Double>(Collections.nCopies(data.size(), 0.0d));

        // the current standard deviation of the rolling window
        List<Double> stdFilter = new ArrayList<Double>(Collections.nCopies(data.size(), 0.0d));

        // init avgFilter and stdFilter
        for (int i = 0; i < lag; i++) {
            stats.addValue(data.get(i));
        }
        avgFilter.set(lag - 1, stats.getMean());
        stdFilter.set(lag - 1, Math.sqrt(stats.getPopulationVariance())); // getStandardDeviation() uses sample variance
        stats.clear();

        // loop input starting at end of rolling window
        for (int i = lag; i < data.size(); i++) {

            // if the distance between the current value and average is enough standard deviations (threshold) away
            if (Math.abs((data.get(i) - avgFilter.get(i - 1))) > threshold * stdFilter.get(i - 1)) {

                // this is a signal (i.e. peak), determine if it is a positive or negative signal
                if (data.get(i) > avgFilter.get(i - 1)) {
                    signals.set(i, 1);
                } else {
                    signals.set(i, -1);
                }

                // filter this signal out using influence
                filteredData.set(i, (influence * data.get(i)) + ((1 - influence) * filteredData.get(i - 1)));
            } else {
                // ensure this signal remains a zero
                signals.set(i, 0);
                // ensure this value is not filtered
                filteredData.set(i, data.get(i));
            }

            // update rolling average and deviation
            for (int j = i - lag; j < i; j++) {
                stats.addValue(filteredData.get(j));
            }
            avgFilter.set(i, stats.getMean());
            stdFilter.set(i, Math.sqrt(stats.getPopulationVariance()));
            stats.clear();
        }

        HashMap<String, List> returnMap = new HashMap<String, List>();
        returnMap.put("signals", signals);
        returnMap.put("filteredData", filteredData);
        returnMap.put("avgFilter", avgFilter);
        returnMap.put("stdFilter", stdFilter);

        return returnMap;

    }

    public void timerSetUp() {
        ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
        scheduledExecutorService.scheduleAtFixedRate(() -> {
            if (timerData.size() > 0) {
                ArrayList<Integer> rPeaks = RPeakDetector.detectRPeaks(timerData);


                // Calculate heart rate from R peaks
                double heartRate = 0;
                for (int i = 1; i < rPeaks.size(); i++) {
                    if(rPeaks.get(i) != rPeaks.get(i-1)) {
                        //System.out.println(rPeaks.get(i) + "               " + rPeaks.get(i - 1));
                        double rrInterval = rPeaks.get(i) - rPeaks.get(i - 1); // time between consecutive R peaks
                        heartRate += (60 / rrInterval); // add heart rate for this R-R interval
                    }
                }
                heartRate /= (rPeaks.size() - 1); // average heart rate

                System.out.println("UCCCCCCCCCCCCCCCCCCCCCC " + heartRate);

                heartRateEditText.setTextIsSelectable(true);
                heartRateEditText.setMovementMethod(LinkMovementMethod.getInstance());
                heartRateEditText.setText(String.valueOf((int)heartRate));
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

    public static double calculateHeartRate(List<Integer> rPeaks, int windowSize) {
        // Get the difference in time between each R peak
        List<Double> rrIntervals = new ArrayList<>();
        for (int i = 1; i < rPeaks.size(); i++) {
            double rrInterval = rPeaks.get(i) - rPeaks.get(i - 1);
            rrIntervals.add(rrInterval);
        }

        // Initialize a moving average window
        double[] window = new double[windowSize];
        int windowIndex = 0;

        // Calculate the moving average of the R-R intervals
        List<Double> movingAvgRRIntervals = new ArrayList<>();
        for (double rrInterval : rrIntervals) {
            window[windowIndex] = rrInterval;
            double sum = 0;
            for (double interval : window) {
                sum += interval;
            }
            double avgRRInterval = sum / windowSize;
            movingAvgRRIntervals.add(avgRRInterval);
            windowIndex = (windowIndex + 1) % windowSize;
        }

        // Calculate heart rate in beats per minute
        double sum = 0;
        for (double avgRRInterval : movingAvgRRIntervals) {
            sum += avgRRInterval;
        }
        double avgRRInterval = sum / movingAvgRRIntervals.size();
        double heartRate = 60 / avgRRInterval;
        return heartRate;
    }

    public static List<Double> shiftLeft(List<Double> list) {
        for (int i = 0; i < list.size() - 1; i++) {
            list.set(i, list.get(i + 1));
        }
        list.remove(list.size()-1);
        return list;
    }


    // endregion
}
