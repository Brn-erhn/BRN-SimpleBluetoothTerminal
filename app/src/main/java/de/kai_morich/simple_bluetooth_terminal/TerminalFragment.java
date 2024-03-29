package de.kai_morich.simple_bluetooth_terminal;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class TerminalFragment extends Fragment implements ServiceConnection, SerialListener {

    private enum Connected { False, Pending, True }

    private String deviceAddress;
    private SerialService service;

    private TextView receiveText;
    private TextView sendText;
    private TextView temp;
    private TextUtil.HexWatcher hexWatcher;

    private Connected connected = Connected.False;
    private boolean initialStart = true;
    private boolean hexEnabled = false;
    private boolean pendingNewline = false;
    private String newline = TextUtil.newline_crlf;

    /*
     * Lifecycle
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
        deviceAddress = getArguments().getString("device");
    }

    @Override
    public void onDestroy() {
        if (connected != Connected.False)
            disconnect();
        getActivity().stopService(new Intent(getActivity(), SerialService.class));
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        if(service != null)
            service.attach(this);
        else
            getActivity().startService(new Intent(getActivity(), SerialService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
    }

    @Override
    public void onStop() {
        if(service != null && !getActivity().isChangingConfigurations())
            service.detach();
        super.onStop();
    }

    @SuppressWarnings("deprecation") // onAttach(context) was added with API 23. onAttach(activity) works for all API versions
    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);
        getActivity().bindService(new Intent(getActivity(), SerialService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDetach() {
        try { getActivity().unbindService(this); } catch(Exception ignored) {}
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        if(initialStart && service != null) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();
        service.attach(this);
        if(initialStart && isResumed()) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }

    /*
     * UI
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_terminal, container, false);
        receiveText = view.findViewById(R.id.receive_text);                          // TextView performance decreases with number of spans
        receiveText.setTextColor(getResources().getColor(R.color.colorRecieveText)); // set as default color to reduce number of spans
        receiveText.setMovementMethod(ScrollingMovementMethod.getInstance());

        sendText = view.findViewById(R.id.send_text);
        hexWatcher = new TextUtil.HexWatcher(sendText);
        hexWatcher.enable(hexEnabled);
        sendText.addTextChangedListener(hexWatcher);
        sendText.setHint(hexEnabled ? "HEX mode" : "");
        Switch gardenSw = view.findViewById(R.id.garden_light);
        Switch roomSw =  view.findViewById(R.id.roomLight);
        Switch coffeeSw = view.findViewById(R.id.coffe);
        Switch blindsSw = view.findViewById(R.id.blinds);
        Switch heatSw = view.findViewById(R.id.heat);
        Switch coolSw = view.findViewById(R.id.cool);
        Switch dripSw = view.findViewById(R.id.drip_system);
        Switch tvSw = view.findViewById(R.id.TV);
        Switch heatS = view.findViewById(R.id.heatSlow);
        Switch heatM = view.findViewById(R.id.heatMed);
        Switch heatF = view.findViewById(R.id.heatFast);
        Switch coolS = view.findViewById(R.id.coolSlow);
        Switch coolM = view.findViewById(R.id.coolMed);
        Switch coolF = view.findViewById(R.id.coolFast);
        Button refresh1 = view.findViewById(R.id.refresh1);
        Button refresh2 = view.findViewById(R.id.refresh2);
        TextView temp = view.findViewById(R.id.temp);
        TextView moist = view.findViewById(R.id.moisture);

        refresh1.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                receiveText.setText("");
                send("C");
                receiveText.setText("");

                Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        String B = receiveText.getText().toString();
                        temp.setText(B + "°");
                        Toast.makeText(getActivity(), "Refreshed", Toast.LENGTH_SHORT).show();
                    }
                }, 4000);
            }

        });


        refresh2.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                receiveText.setText("");
                send("P");
                receiveText.setText("");

                Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        String M = receiveText.getText().toString();
                        moist.setText("%"+ M);
                        Toast.makeText(getActivity(), "Refreshed", Toast.LENGTH_SHORT).show();
                    }
                }, 4000);
            }

        });






        dripSw.setOnClickListener(v->
        {

            if ( dripSw.isChecked()) {

                send("s");


            }
            else{

                send("t");
            }




        });




        tvSw.setOnClickListener(v->
        {

            if ( tvSw.isChecked()) {

                send("5");
                Toast.makeText(getActivity(), "TV ON.", Toast.LENGTH_SHORT).show();


            }
            else{

                send("6");
            }




        });



        gardenSw.setOnClickListener( v->
        {
            if (gardenSw.isChecked()) {
                send("7");

            }
            else{
                send("8");
            }
        });

        roomSw.setOnClickListener(view1 ->
                {
                    if(roomSw.isChecked()){

                        send("R");

                    }

                    else{
                        send("r");
                    }


                }
                );



        coffeeSw.setOnClickListener(v->
                {
                    if (coffeeSw.isChecked()){

                        send("c");

                    }

                    else{

                        send("c");
                    }

                }
                );



        blindsSw.setOnClickListener(v->
                {
                    if (blindsSw.isChecked()){

                        send("B");

                    }
                    else{

                        send("b");
                    }

                }
                );



        heatSw.setOnClickListener(v->
                {
                    if (heatSw.isChecked()){


                        Toast.makeText(getActivity(), "Please choose speed.", Toast.LENGTH_SHORT).show();

                    }

                    else {
                        send("4");

                        Toast.makeText(getActivity(), "Heater fan off.", Toast.LENGTH_SHORT).show();
                    }

                }
                );




        heatS.setOnClickListener(v->
                {
                    if (heatSw.isChecked()){

                        if(heatS.isChecked()){
                            send("L");
                            Toast.makeText(getActivity(),"Heater fan speed LOW",Toast.LENGTH_SHORT).show();

                        }
                        else{
                            send("4");
                            Toast.makeText(getActivity(),"Heater fan off",Toast.LENGTH_SHORT).show();



                        }


                    }
                    else{

                        Toast.makeText(getActivity(),"Please open the heater",Toast.LENGTH_SHORT).show();

                    }




                }

                );
        heatM.setOnClickListener(v->
                {
                    if (heatSw.isChecked()){

                        if(heatM.isChecked()){
                            send("M");
                            Toast.makeText(getActivity(),"Heater fan speed MED",Toast.LENGTH_SHORT).show();

                        }
                        else{
                            send("4");
                            Toast.makeText(getActivity(),"Heater fan off",Toast.LENGTH_SHORT).show();



                        }

                    }
                    else{

                        Toast.makeText(getActivity(),"Please open the heater",Toast.LENGTH_SHORT).show();
                    }




                }

                );
        heatF.setOnClickListener(v->
                {
                    if (heatSw.isChecked()){

                        if(heatF.isChecked()){
                            send("H");
                            Toast.makeText(getActivity(),"Heater fan speed HIGH",Toast.LENGTH_SHORT).show();

                        }
                        else{
                            send("4");
                            Toast.makeText(getActivity(),"Heater fan off",Toast.LENGTH_SHORT).show();



                        }


                    }
                    else{

                        Toast.makeText(getActivity(),"Please open the heater",Toast.LENGTH_SHORT).show();
                    }


                }

                );
        coolSw.setOnClickListener(v->
                {
                    if (coolSw.isChecked()){


                        Toast.makeText(getActivity(), "Please choose speed.", Toast.LENGTH_SHORT).show();

                    }

                    else {
                        send("2");

                        Toast.makeText(getActivity(), "Cooler fan off.", Toast.LENGTH_SHORT).show();
                    }

                }
        );




        coolS.setOnClickListener(v->
                {
                    if (coolSw.isChecked()){

                        if(coolS.isChecked()){
                            send("l");
                            Toast.makeText(getActivity(),"Cooler fan speed LOW",Toast.LENGTH_SHORT).show();

                        }
                        else{

                            Toast.makeText(getActivity(),"Cooler fan off",Toast.LENGTH_SHORT).show();
                            send("2");



                        }
                    }
                    else{

                        Toast.makeText(getActivity(),"Please open the cooler",Toast.LENGTH_SHORT).show();

                    }




                }

        );
        coolM.setOnClickListener(v->
                {
                    if (coolSw.isChecked()){

                        if(coolM.isChecked()){
                            send("m");
                            Toast.makeText(getActivity(),"Cooler fan speed MED",Toast.LENGTH_SHORT).show();

                        }
                        else{

                            Toast.makeText(getActivity(),"Cooler fan off",Toast.LENGTH_SHORT).show();
                            send("2");



                        }
                    }
                    else{

                        Toast.makeText(getActivity(),"Please open the cooler",Toast.LENGTH_SHORT).show();
                    }




                }

        );
        coolF.setOnClickListener(v->
                {
                    if (coolSw.isChecked()){

                        if(coolF.isChecked()){
                            send("h");
                            Toast.makeText(getActivity(),"Cooler fan speed HIGH",Toast.LENGTH_SHORT).show();

                        }
                        else{

                            Toast.makeText(getActivity(),"Cooler fan off",Toast.LENGTH_SHORT).show();
                            send("2");



                        }
                    }
                    else{

                        Toast.makeText(getActivity(),"Please open the cooler",Toast.LENGTH_SHORT).show();
                    }


                }

        );



       // View sendBtn = view.findViewById(R.id.send_btn);
       // View gardenBtn = view.findViewById(R.id.garden);
       // View roomBtn = view.findViewById(R.id.rooms);
       // View tvBtn = view.findViewById(R.id.tv);
       // View blindBtn = view.findViewById(R.id.blinds);
       // View coffeBtn = view.findViewById(R.id.coffe);

       //sendBtn.setOnClickListener(v -> send(sendText.getText().toString()));
       //gardenBtn.setOnClickListener(v -> send("7"));
       //roomBtn.setOnClickListener(v -> send("R"));
       //tvBtn.setOnClickListener(v -> send("5"));
       //blindBtn.setOnClickListener(v -> send("B"));
       //coffeBtn.setOnClickListener(v -> send("c"));






        return view;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_terminal, menu);
        menu.findItem(R.id.hex).setChecked(hexEnabled);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.clear) {
            receiveText.setText("");
            return true;
        } else if (id == R.id.newline) {
            String[] newlineNames = getResources().getStringArray(R.array.newline_names);
            String[] newlineValues = getResources().getStringArray(R.array.newline_values);
            int pos = java.util.Arrays.asList(newlineValues).indexOf(newline);
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("Newline");
            builder.setSingleChoiceItems(newlineNames, pos, (dialog, item1) -> {
                newline = newlineValues[item1];
                dialog.dismiss();
            });
            builder.create().show();
            return true;
        } else if (id == R.id.hex) {
            hexEnabled = !hexEnabled;
            sendText.setText("");
            hexWatcher.enable(hexEnabled);
            sendText.setHint(hexEnabled ? "HEX mode" : "");
            item.setChecked(hexEnabled);
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    /*
     * Serial + UI
     */
    private void connect() {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            status("connecting...");
            connected = Connected.Pending;
            SerialSocket socket = new SerialSocket(getActivity().getApplicationContext(), device);
            service.connect(socket);

        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    private void disconnect() {
        connected = Connected.False;
        service.disconnect();
    }

    private void send(String str) {
        if(connected != Connected.True) {
            Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String msg;
            byte[] data;
            if(hexEnabled) {
                StringBuilder sb = new StringBuilder();
                TextUtil.toHexString(sb, TextUtil.fromHexString(str));
                TextUtil.toHexString(sb, newline.getBytes());
                msg = sb.toString();
                data = TextUtil.fromHexString(msg);
            } else {
                msg = str;
                data = (str + newline).getBytes();
            }
            SpannableStringBuilder spn = new SpannableStringBuilder(msg + '\n');
            spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            receiveText.append(spn);
            service.write(data);
        } catch (Exception e) {
            onSerialIoError(e);
        }
    }

    private void receive(byte[] data) {
        if(hexEnabled) {
            receiveText.append(TextUtil.toHexString(data) + '\n');
        } else {
            String msg = new String(data);
            if(newline.equals(TextUtil.newline_crlf) && msg.length() > 0) {
                // don't show CR as ^M if directly before LF
                msg = msg.replace(TextUtil.newline_crlf, TextUtil.newline_lf);
                // special handling if CR and LF come in separate fragments
                if (pendingNewline && msg.charAt(0) == '\n') {
                    Editable edt = receiveText.getEditableText();
                    if (edt != null && edt.length() > 1)
                        edt.replace(edt.length() - 2, edt.length(), "");
                }
                pendingNewline = msg.charAt(msg.length() - 1) == '\r';
            }
            receiveText.append(TextUtil.toCaretString(msg, newline.length() != 0));
        }
    }

    private void status(String str) {
        SpannableStringBuilder spn = new SpannableStringBuilder(str + '\n');
        spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        receiveText.append(spn);
    }

    /*
     * SerialListener
     */
    @Override
    public void onSerialConnect() {
        status("connected");
        connected = Connected.True;
    }

    @Override
    public void onSerialConnectError(Exception e) {
        status("connection failed: " + e.getMessage());
        disconnect();
    }

    @Override
    public void onSerialRead(byte[] data) {
        receive(data);
    }

    @Override
    public void onSerialIoError(Exception e) {
        status("connection lost: " + e.getMessage());
        disconnect();
    }

}
