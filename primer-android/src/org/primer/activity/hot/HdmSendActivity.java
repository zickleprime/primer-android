/*
 *
 *  * Copyright 2014 http://Bither.net
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *    http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package org.primer.activity.hot;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import org.primer.PrimerSetting;
import org.primer.R;
import org.primer.primerj.PrimerjSettings;
import org.primer.primerj.api.SignatureHDMApi;
import org.primer.primerj.api.http.Http400Exception;
import org.primer.primerj.api.http.HttpSetting;
import org.primer.primerj.core.AddressManager;
import org.primer.primerj.core.HDMAddress;
import org.primer.primerj.core.HDMBId;
import org.primer.primerj.core.Tx;
import org.primer.primerj.crypto.ECKey;
import org.primer.primerj.crypto.KeyCrypterException;
import org.primer.primerj.crypto.SecureCharSequence;
import org.primer.primerj.crypto.TransactionSignature;
import org.primer.primerj.exception.PasswordException;
import org.primer.primerj.qrcode.QRCodeTxTransport;
import org.primer.primerj.qrcode.QRCodeUtil;
import org.primer.primerj.utils.Utils;
import org.primer.model.Ticker;
import org.primer.preference.AppSharedPreference;
import org.primer.qrcode.ScanActivity;
import org.primer.runnable.CommitTransactionThread;
import org.primer.runnable.CompleteTransactionRunnable;
import org.primer.runnable.HandlerMessage;
import org.primer.runnable.RCheckRunnable;
import org.primer.ui.base.CurrencyAmountView;
import org.primer.ui.base.CurrencyCalculatorLink;
import org.primer.ui.base.DropdownMessage;
import org.primer.ui.base.SwipeRightActivity;
import org.primer.ui.base.dialog.DialogConfirmTask;
import org.primer.ui.base.dialog.DialogRCheck;
import org.primer.ui.base.dialog.DialogSelectChangeAddress;
import org.primer.ui.base.dialog.DialogSendConfirm;
import org.primer.ui.base.dialog.DialogSendConfirm.SendConfirmListener;
import org.primer.ui.base.dialog.DialogWithActions;
import org.primer.ui.base.keyboard.EntryKeyboardView;
import org.primer.ui.base.keyboard.amount.AmountEntryKeyboardView;
import org.primer.ui.base.keyboard.password.PasswordEntryKeyboardView;
import org.primer.ui.base.listener.IBackClickListener;
import org.primer.util.BroadcastUtil;
import org.primer.util.HDMResetServerPasswordUtil;
import org.primer.util.InputParser.StringInputParser;
import org.primer.util.MarketUtil;
import org.primer.util.UnitUtilWrapper;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class HdmSendActivity extends SwipeRightActivity implements EntryKeyboardView.EntryKeyboardViewListener, CommitTransactionThread.CommitTransactionListener {
    private int addressPosition;
    private HDMAddress address;
    private TextView tvAddressLabel;

    private EditText etAddress;
    private EditText etPassword;
    private ImageButton ibtnScan;
    private CurrencyCalculatorLink amountCalculatorLink;
    private Button btnSend;
    private DialogRCheck dp;
    private TextView tvBalance;
    private ImageView ivBalanceSymbol;
    private PasswordEntryKeyboardView kvPassword;
    private AmountEntryKeyboardView kvAmount;
    private View vKeyboardContainer;
    private DialogSelectChangeAddress dialogSelectChangeAddress;

    private HDMResetServerPasswordUtil resetServerPasswordUtil;

    private boolean signWithCold = false;
    private boolean isInRecovery = false;
    private boolean isDonate = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(R.anim.slide_in_right, 0);
        setContentView(R.layout.activity_send);
        if (getIntent().getExtras().containsKey(PrimerSetting.INTENT_REF
                .ADDRESS_POSITION_PASS_VALUE_TAG)) {
            addressPosition = getIntent().getExtras().getInt(PrimerSetting.INTENT_REF
                    .ADDRESS_POSITION_PASS_VALUE_TAG);
            if (addressPosition >= 0 && addressPosition < AddressManager.getInstance()
                    .getHdmKeychain().getAddresses().size()) {
                address = AddressManager.getInstance().getHdmKeychain().getAddresses().get
                        (addressPosition);
            }
        }
        if (address == null) {
            finish();
            return;
        }
        isInRecovery = address.isInRecovery();
        initView();
        processIntent();
        configureDonate();
    }

    private void initView() {
        findViewById(R.id.ibtn_cancel).setOnClickListener(new IBackClickListener());
        tvAddressLabel = (TextView) findViewById(R.id.tv_address_label);
        etAddress = (EditText) findViewById(R.id.et_address);
        ibtnScan = (ImageButton) findViewById(R.id.ibtn_scan);
        btnSend = (Button) findViewById(R.id.btn_send);
        etPassword = (EditText) findViewById(R.id.et_password);
        tvBalance = (TextView) findViewById(R.id.tv_balance);
        ivBalanceSymbol = (ImageView) findViewById(R.id.iv_balance_symbol);
        kvPassword = (PasswordEntryKeyboardView) findViewById(R.id.kv_password);
        kvAmount = (AmountEntryKeyboardView) findViewById(R.id.kv_amount);
        vKeyboardContainer = findViewById(R.id.v_keyboard_container);
        findViewById(R.id.ibtn_option).setOnClickListener(optionClick);
        dialogSelectChangeAddress = new DialogSelectChangeAddress(this, address);
        tvBalance.setText(UnitUtilWrapper.formatValue(address.getBalance()));
        ivBalanceSymbol.setImageBitmap(UnitUtilWrapper.getBtcSymbol(tvBalance));
        etPassword.addTextChangedListener(passwordWatcher);
        etPassword.setOnEditorActionListener(passwordAction);
        final CurrencyAmountView btcAmountView = (CurrencyAmountView) findViewById(R.id.cav_btc);
        btcAmountView.setCurrencySymbol(getString(R.string.bitcoin_symbol));
        int precision = (int) Math.floor(Math.log10(AppSharedPreference.getInstance()
                .getBitcoinUnit().satoshis));
        btcAmountView.setInputPrecision(precision);
        btcAmountView.setHintPrecision(Math.min(4, precision));
        btcAmountView.setShift(8 - precision);

        final CurrencyAmountView localAmountView = (CurrencyAmountView) findViewById(R.id
                .cav_local);
        localAmountView.setInputPrecision(2);
        localAmountView.setHintPrecision(2);
        amountCalculatorLink = new CurrencyCalculatorLink(btcAmountView, localAmountView);
        ReceivingAddressListener addressListener = new ReceivingAddressListener();
        etAddress.setOnFocusChangeListener(addressListener);
        etAddress.addTextChangedListener(addressListener);
        dp = new DialogRCheck(this);
        ibtnScan.setOnClickListener(scanClick);
        btnSend.setOnClickListener(sendClick);
        kvPassword.registerEditText(etPassword).setListener(this);
        kvAmount.registerEditText((EditText) findViewById(R.id.send_coins_amount_btc_edittext),
                (EditText) findViewById(R.id.send_coins_amount_local_edittext)).setListener(this);
        findViewById(R.id.ll_balance).setOnClickListener(balanceClick);
        resetServerPasswordUtil = new HDMResetServerPasswordUtil(this, dp);
        configureForOtherSignPart();
    }

    private OnClickListener balanceClick = new OnClickListener() {
        @Override
        public void onClick(View v) {
            amountCalculatorLink.setBtcAmount(address.getBalance());
        }
    };


    private OnClickListener scanClick = new OnClickListener() {

        @Override
        public void onClick(View v) {
            Intent intent = new Intent(HdmSendActivity.this, ScanActivity.class);
            startActivityForResult(intent, PrimerSetting.INTENT_REF.SCAN_REQUEST_CODE);
        }
    };

    private SendConfirmListener sendConfirmListener = new SendConfirmListener() {

        @Override
        public void onConfirm(Tx tx) {
            etPassword.setText("");
            try {
                dp.setWait();
                dp.setCancelable(false);
                if (!dp.isShowing()) {
                    dp.show();
                }
                new CommitTransactionThread(dp, addressPosition, tx, true, true, HdmSendActivity.this).start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onCancel() {

        }
    };

    private Handler completeTransactionHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case HandlerMessage.MSG_SUCCESS:
                    if (!dp.isShowing()) {
                        dp.show();
                    }
                    if (msg.obj != null && msg.obj instanceof Tx) {
                        Tx tx = (Tx) msg.obj;
                        //dp.setRChecking();
                        RCheckRunnable run = new RCheckRunnable(address, tx);
                        run.setHandler(rcheckHandler);
                        new Thread(run).start();
                    } else {
                        DropdownMessage.showDropdownMessage(HdmSendActivity.this,
                                R.string.password_wrong);
                    }
                    break;
                case HandlerMessage.MSG_PASSWORD_WRONG:
                    if (dp.isShowing()) {
                        dp.dismiss();
                    }
                    DropdownMessage.showDropdownMessage(HdmSendActivity.this,
                            R.string.password_wrong);
                    break;
                case HandlerMessage.MSG_FAILURE:
                    if (dp.isShowing()) {
                        dp.dismiss();
                    }
                    if (msg.obj != null) {
                        String msgError = getString(R.string.send_failed);
                        if (msg.obj instanceof String) {
                            msgError = (String) msg.obj;
                        }
                        DropdownMessage.showDropdownMessage(HdmSendActivity.this, msgError);
                    }
                    break;
                default:
                    break;
            }
        }
    };

    private Handler rcheckHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case HandlerMessage.MSG_SUCCESS:
                    if (msg.obj != null && msg.obj instanceof Tx) {
                        final Tx tx = (Tx) msg.obj;
                        // dp.setRCheckSuccess();
                        if (!dp.isShowing()) {
                            dp.show();
                        }
                        tvAddressLabel.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                dp.dismiss();
                                if (signWithCold || isInRecovery) {
                                    sendConfirmListener.onConfirm(tx);
                                } else {
                                    DialogSendConfirm dialog = new DialogSendConfirm(HdmSendActivity
                                            .this, tx, dialogSelectChangeAddress.getChangeAddress
                                            ().equals(address) ? null : dialogSelectChangeAddress
                                            .getChangeAddress().getAddress(), sendConfirmListener);
                                    dialog.show();
                                }
                                dp.setWait();
                            }
                        }, 800);
                        break;
                    }
                case HandlerMessage.MSG_FAILURE:
                    //TODO need more complicated logic to recalculate r,
                    // because rfc6979 will use the same r for the same transaction
                    // dp.setRecalculatingR();
                    if (!dp.isShowing()) {
                        dp.show();
                    }
                    sendClick.onClick(btnSend);
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    public void onCommitTransactionSuccess(Tx tx) {
        Intent intent = getIntent();
        if (tx != null) {
            intent.putExtra(SelectAddressToSendActivity.INTENT_EXTRA_TRANSACTION,
                    tx.getHashAsString());
        }
        setResult(RESULT_OK, intent);
        finish();
    }

    @Override
    public void onCommitTransactionFailed() {
        DropdownMessage.showDropdownMessage(HdmSendActivity.this, R.string.send_failed);
    }

    private OnClickListener sendClick = new OnClickListener() {

        @Override
        public void onClick(View v) {
            final long btc = amountCalculatorLink.getAmount();
            if (btc > 0) {
                if (Utils.validBicoinAddress(etAddress.getText().toString().trim())) {
                    if (Utils.compareString(etAddress.getText().toString().trim(),
                            dialogSelectChangeAddress.getChangeAddress().getAddress())) {
                        DropdownMessage.showDropdownMessage(HdmSendActivity.this,
                                R.string.select_change_address_change_to_same_warn);
                        return;
                    }
                    try {
                        CompleteTransactionRunnable completeRunnable;
                        if (!isInRecovery) {
                            completeRunnable = new CompleteTransactionRunnable(addressPosition,
                                    amountCalculatorLink.getAmount(),
                                    etAddress.getText().toString().trim(),
                                    dialogSelectChangeAddress.getChangeAddress().getAddress(),
                                    new SecureCharSequence(etPassword.getText()),
                                    signWithCold ? coldSignatureFetcher : remoteSignatureFetcher);
                        } else {
                            completeRunnable = new CompleteTransactionRunnable(addressPosition,
                                    amountCalculatorLink.getAmount(),
                                    etAddress.getText().toString().trim(),
                                    dialogSelectChangeAddress.getChangeAddress().getAddress(),
                                    new SecureCharSequence(etPassword.getText()),
                                    coldSignatureFetcher, remoteSignatureFetcher);
                        }
                        completeRunnable.setHandler(completeTransactionHandler);
                        Thread thread = new Thread(completeRunnable);
                        dp.setThread(thread);
                        if (!dp.isShowing()) {
                            dp.show();
                        }
                        thread.start();
                    } catch (Exception e) {
                        e.printStackTrace();
                        DropdownMessage.showDropdownMessage(HdmSendActivity.this,
                                R.string.send_failed);
                    }
                } else {
                    DropdownMessage.showDropdownMessage(HdmSendActivity.this, R.string.send_failed);
                }
            }
        }
    };

    private TextView.OnEditorActionListener passwordAction = new TextView.OnEditorActionListener() {
        @Override
        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                if (btnSend.isEnabled()) {
                    sendClick.onClick(btnSend);
                }
                return true;
            }
            return false;
        }
    };

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PrimerSetting.INTENT_REF.SCAN_REQUEST_CODE && resultCode == Activity
                .RESULT_OK) {
            final String input = data.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT);
            new StringInputParser(input, null) {
                @Override
                protected void bitcoinRequest(final String address, final String addressLabel,
                                              final long amount, final String bluetoothMac) {
                    etAddress.setText(address.toString());
                    if (amount > 0) {
                        amountCalculatorLink.setBtcAmount(amount);
                        etPassword.requestFocus();
                    } else {
                        amountCalculatorLink.requestFocus();
                    }
                    validateValues();
                }

                @Override
                protected void error(final int messageResId, final Object... messageArgs) {
                    DropdownMessage.showDropdownMessage(HdmSendActivity.this,
                            R.string.scan_watch_only_address_error);
                }
            }.parse();
        } else if (!coldSignatureFetcher.onActivityResult(requestCode, resultCode, data) && !resetServerPasswordUtil.onActivityResult(requestCode, resultCode, data)) {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onEntryKeyboardShow(EntryKeyboardView v) {
        vKeyboardContainer.setVisibility(View.VISIBLE);
    }

    @Override
    public void onEntryKeyboardHide(EntryKeyboardView v) {
        vKeyboardContainer.setVisibility(View.GONE);
    }

    private final class ReceivingAddressListener implements OnFocusChangeListener, TextWatcher {
        @Override
        public void onFocusChange(final View v, final boolean hasFocus) {
            if (!hasFocus) {
                validateValues();
            }
        }

        @Override
        public void afterTextChanged(final Editable s) {
            validateValues();
        }

        @Override
        public void beforeTextChanged(final CharSequence s, final int start, final int count,
                                      final int after) {
        }

        @Override
        public void onTextChanged(final CharSequence s, final int start, final int before,
                                  final int count) {
        }
    }

    private final CurrencyAmountView.Listener amountsListener = new CurrencyAmountView.Listener() {
        @Override
        public void changed() {
            validateValues();
        }

        @Override
        public void done() {
            validateValues();
            btnSend.requestFocusFromTouch();
        }

        @Override
        public void focusChanged(final boolean hasFocus) {
            if (!hasFocus) {
                validateValues();
            }
        }
    };

    private TextWatcher passwordWatcher = new TextWatcher() {

        private SecureCharSequence password;

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {

        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            if (password != null) {
                password.wipe();
            }
            password = new SecureCharSequence(etPassword.getText());
        }

        @Override
        public void afterTextChanged(Editable s) {
            SecureCharSequence p = new SecureCharSequence(etPassword.getText());
            if (p.length() > 0) {
                if (!Utils.validPassword(p)) {
                    etPassword.setText(password);
                }
            }
            p.wipe();
            validateValues();
            password.wipe();
        }
    };

    private void validateValues() {
        boolean isValidAmounts = false;

        final BigInteger amount = BigInteger.valueOf(amountCalculatorLink.getAmount());

        if (amount == null) {
        } else if (amount.signum() > 0) {
            isValidAmounts = true;
        } else {
        }
        String address = etAddress.getText().toString().trim();
        boolean isValidAddress = Utils.validBicoinAddress(address);
        SecureCharSequence password = new SecureCharSequence(etPassword.getText());
        boolean isValidPassword = Utils.validPassword(password) && password.length() >= 6 &&
                password.length() <= getResources().getInteger(R.integer.password_length_max);
        password.wipe();
        btnSend.setEnabled(isValidAddress && isValidAmounts && isValidPassword);
    }

    private double getExchangeRate() {
        Ticker ticker = MarketUtil.getTickerOfDefaultMarket();
        if (ticker != null) {
            return ticker.getDefaultExchangePrice();
        }
        return 0;
    }

    private BroadcastReceiver marketBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            amountCalculatorLink.setExchangeRate(getExchangeRate());
        }
    };

    private DialogWithActions.DialogWithActionsClickListener optionClick = new DialogWithActions
            .DialogWithActionsClickListener() {

        @Override
        protected List<DialogWithActions.Action> getActions() {
            ArrayList<DialogWithActions.Action> actions = new ArrayList<DialogWithActions.Action>();
            actions.add(new DialogWithActions.Action(R.string.select_change_address_option_name,
                    new Runnable() {
                        @Override
                        public void run() {
                            dialogSelectChangeAddress.show();
                        }
                    }));
            if (isInRecovery) {
                return actions;
            }
            if (signWithCold) {
                actions.add(new DialogWithActions.Action(R.string.hdm_send_with_server,
                        new Runnable() {
                            @Override
                            public void run() {
                                signWithCold = false;
                                configureForOtherSignPart();
                            }
                        }));
            } else {
                actions.add(new DialogWithActions.Action(R.string.hdm_send_with_cold,
                        new Runnable() {
                            @Override
                            public void run() {
                                signWithCold = true;
                                configureForOtherSignPart();
                            }
                        }));
            }
            return actions;
        }
    };

    private void configureForOtherSignPart() {
        if (signWithCold || isInRecovery) {
            btnSend.setCompoundDrawablesWithIntrinsicBounds(R.drawable
                            .unsigned_transaction_button_icon_mirror_transparent, 0,
                    R.drawable.unsigned_transaction_button_icon, 0);
        } else {
            btnSend.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        amountCalculatorLink.setListener(amountsListener);
        amountCalculatorLink.setExchangeRate(getExchangeRate());
        IntentFilter marketFilter = new IntentFilter(BroadcastUtil.ACTION_MARKET);
        registerReceiver(marketBroadcastReceiver, marketFilter);
    }

    @Override
    protected void onPause() {
        amountCalculatorLink.setListener(null);
        unregisterReceiver(marketBroadcastReceiver);
        super.onPause();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, R.anim.slide_out_right);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private void processIntent() {
        isDonate = false;
        Intent intent = getIntent();
        if (intent.hasExtra(SelectAddressToSendActivity.INTENT_EXTRA_ADDRESS)) {
            String address = intent.getExtras().getString(SelectAddressToSendActivity
                    .INTENT_EXTRA_ADDRESS);
            if (Utils.validBicoinAddress(address)) {
                if (Utils.compareString(address, PrimerjSettings.DONATE_ADDRESS)) {
                    isDonate = true;
                }
                etAddress.setText(address);
                long btc = intent.getExtras().getLong(SelectAddressToSendActivity
                        .INTENT_EXTRA_AMOUNT, 0);
                if (btc > 0) {
                    amountCalculatorLink.setBtcAmount(btc);
                }
                validateValues();
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (!kvPassword.handleBack()) {
            super.onBackPressed();
        }
    }

    private void configureDonate() {
        if (isDonate) {
            btnSend.setText(R.string.donate_sending_verb);
            tvAddressLabel.setText(R.string.donate_receiving_address_label);
            etAddress.setEnabled(false);
            etAddress.clearFocus();
            ibtnScan.setVisibility(View.GONE);
        } else {
            btnSend.setText(R.string.address_detail_send);
            tvAddressLabel.setText(R.string.send_coins_fragment_receiving_address_label);
            etAddress.setEnabled(true);
            ibtnScan.setVisibility(View.VISIBLE);
        }
    }

    private class ColdSignatureFetcher implements HDMAddress.HDMFetchOtherSignatureDelegate {
        private static final int RequestCode = 1251;

        private ReentrantLock lock = new ReentrantLock();
        private Condition fetchedCondition = lock.newCondition();
        private List<TransactionSignature> sigs;
        private int signingIndex = -1;

        @Override
        public List<TransactionSignature> getOtherSignature(int addressIndex,
                                                            CharSequence password,
                                                            List<byte[]> unsignHash, final Tx tx) {
            signingIndex = addressIndex;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    DialogSendConfirm dialog = new DialogSendConfirm(HdmSendActivity.this, tx,
                            dialogSelectChangeAddress.getChangeAddress().equals(address) ? null :
                                    dialogSelectChangeAddress.getChangeAddress().getAddress(),
                            preConfirmListener);
                    dialog.show();
                }
            });
            sigs = null;
            try {
                lock.lockInterruptibly();
                fetchedCondition.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                lock.unlock();
            }
            if (sigs == null) {
                throw new CompleteTransactionRunnable.HDMSignUserCancelExcetion();
            }
            signingIndex = -1;
            return sigs;
        }

        private SendConfirmListener preConfirmListener = new SendConfirmListener() {

            @Override
            public void onConfirm(Tx tx) {
                String addressCannotBtParsed = getString(R.string.address_cannot_be_parsed);
                Intent intent = new Intent(HdmSendActivity.this, UnsignedTxQrCodeActivity.class);
                String changeAddress = dialogSelectChangeAddress.getChangeAddress().equals
                        (address) ? null : dialogSelectChangeAddress.getChangeAddress()
                        .getAddress();
                intent.putExtra(PrimerSetting.INTENT_REF.QR_CODE_STRING,
                        QRCodeTxTransport.getPresignTxString(tx, changeAddress,
                                addressCannotBtParsed, signingIndex));
                if (Utils.isEmpty(changeAddress)) {
                    intent.putExtra(PrimerSetting.INTENT_REF.OLD_QR_CODE_STRING,
                            QRCodeTxTransport.oldGetPreSignString(tx, addressCannotBtParsed));
                } else {
                    intent.putExtra(PrimerSetting.INTENT_REF.QR_CODE_HAS_CHANGE_ADDRESS_STRING,
                            true);
                }

                intent.putExtra(PrimerSetting.INTENT_REF.TITLE_STRING,
                        getString(R.string.unsigned_transaction_qr_code_title));
                startActivityForResult(intent, RequestCode);
                sigs = null;
            }

            @Override
            public void onCancel() {
                sigs = null;
                try {
                    lock.lock();
                    fetchedCondition.signal();
                } finally {
                    lock.unlock();
                }
            }
        };

        public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
            if (requestCode == RequestCode) {
                if (resultCode == RESULT_OK) {
                    final String qr = data.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT);
                    try {
                        String[] stringArray = QRCodeUtil.splitString(qr);
                        sigs = new ArrayList<TransactionSignature>();
                        for (String str : stringArray) {
                            if (!Utils.isEmpty(str)) {
                                TransactionSignature transactionSignature = new
                                        TransactionSignature(ECKey.ECDSASignature.decodeFromDER
                                        (Utils.hexStringToByteArray(str)),
                                        TransactionSignature.SigHash.ALL, false);
                                sigs.add(transactionSignature);
                            }
                        }
                    } catch (Exception e) {
                        sigs = null;
                        e.printStackTrace();
                        DropdownMessage.showDropdownMessage(HdmSendActivity.this,
                                R.string.send_failed);
                    }
                } else {
                    sigs = null;
                }
                try {
                    lock.lock();
                    fetchedCondition.signal();
                } finally {
                    lock.unlock();
                }
                return true;
            }
            return false;
        }
    }

    private ColdSignatureFetcher coldSignatureFetcher = new ColdSignatureFetcher();

    private HDMAddress.HDMFetchOtherSignatureDelegate remoteSignatureFetcher = new HDMAddress
            .HDMFetchOtherSignatureDelegate() {
        private boolean toChangePassword = false;

        @Override
        public List<TransactionSignature> getOtherSignature(int addressIndex,
                                                            CharSequence password,
                                                            List<byte[]> unsignHash, Tx tx) {
            List<TransactionSignature> transactionSignatureList = new
                    ArrayList<TransactionSignature>();
            try {
                HDMBId hdmbId = HDMBId.getHDMBidFromDb();
                byte[] decryptedPassword = hdmbId.decryptHDMBIdPassword(password);
                SignatureHDMApi signatureHDMApi = new SignatureHDMApi(HDMBId.getHDMBidFromDb()
                        .getAddress(), addressIndex, decryptedPassword, unsignHash);
                signatureHDMApi.handleHttpPost();
                List<byte[]> bytesList = signatureHDMApi.getResult();
                for (byte[] bytes : bytesList) {
                    TransactionSignature transactionSignature = new TransactionSignature(ECKey
                            .ECDSASignature.decodeFromDER(bytes), TransactionSignature.SigHash
                            .ALL, false);
                    transactionSignatureList.add(transactionSignature);
                }
            } catch (Exception e) {
                if (e instanceof Http400Exception) {
                    if (((Http400Exception) e).getErrorCode() == HttpSetting.PasswordWrong) {
                        toChangePassword = false;
                        final ReentrantLock lock = new ReentrantLock();
                        final Condition changePasswordCondition = lock.newCondition();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (dp.isShowing()) {
                                    dp.dismiss();
                                }
                                new DialogConfirmTask(HdmSendActivity.this, getString(R.string.hdm_reset_server_password_password_wrong_confirm),
                                        new Runnable() {
                                            @Override
                                            public void run() {
                                                toChangePassword = true;
                                                try {
                                                    lock.lock();
                                                    changePasswordCondition.signal();
                                                } finally {
                                                    lock.unlock();
                                                }
                                            }
                                        }, new Runnable() {
                                    @Override
                                    public void run() {
                                        toChangePassword = false;
                                        try {
                                            lock.lock();
                                            changePasswordCondition.signal();
                                        } finally {
                                            lock.unlock();
                                        }
                                    }
                                }).show();
                            }
                        });
                        try {
                            lock.lock();
                            changePasswordCondition.awaitUninterruptibly();
                        } finally {
                            lock.unlock();
                        }
                        if (!toChangePassword) {
                            throw new CompleteTransactionRunnable.HDMSignUserCancelExcetion();
                        }
                        resetServerPasswordUtil.setPassword(password);
                        if (!resetServerPasswordUtil.changePassword()) {
                            throw new CompleteTransactionRunnable.HDMSignUserCancelExcetion();
                        }
                        return getOtherSignature(addressIndex, password, unsignHash, tx);
                    } else {
                        throw new CompleteTransactionRunnable.HDMServerSignException(R.string
                                .hdm_address_sign_tx_server_error);
                    }
                } else if (e instanceof KeyCrypterException) {
                    throw new PasswordException("hdm password decrypting error");
                } else {
                    throw new RuntimeException(e);
                }
            }

            return transactionSignatureList;
        }
    };
}
