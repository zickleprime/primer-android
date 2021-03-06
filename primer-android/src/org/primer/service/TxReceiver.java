package org.primer.service;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.primer.PrimerApplication;
import org.primer.PrimerSetting;
import org.primer.NotificationAndroidImpl;
import org.primer.R;
import org.primer.activity.hot.HotActivity;
import org.primer.primerj.core.HDAccount;
import org.primer.primerj.core.Tx;
import org.primer.primerj.utils.Utils;
import org.primer.preference.AppSharedPreference;
import org.primer.util.SystemUtil;
import org.primer.util.UnitUtilWrapper;


public class TxReceiver extends BroadcastReceiver {

    private BlockchainService blockchainService;
    private NotificationManager nm;
    private TickReceiver tickReceiver;

    public TxReceiver(BlockchainService service, TickReceiver tickReceiver) {
        this.blockchainService = service;
        this.tickReceiver = tickReceiver;
        nm = (NotificationManager) service.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Utils.compareString(intent.getAction(), NotificationAndroidImpl.ACTION_ADDRESS_BALANCE)) {
            return;
        }
        if (tickReceiver != null) {
            tickReceiver.setTransactionsReceived();
        }
        String address = intent.getStringExtra(NotificationAndroidImpl.MESSAGE_ADDRESS);
        long amount = intent.getLongExtra(NotificationAndroidImpl.MESSAGE_DELTA_BALANCE, 0);
        int txNotificationType = intent.getIntExtra(NotificationAndroidImpl.MESSAGE_TX_NOTIFICATION_TYPE, 0);
        if (txNotificationType == Tx.TxNotificationType.txReceive.getValue()) {
            boolean isReceived = amount > 0;
            amount = Math.abs(amount);
            notifyCoins(address, amount, isReceived);
        }

    }

    private void notifyCoins(String address, final long amount,
                             boolean isReceived) {
        String contentText = address;
        if (Utils.compareString(address, HDAccount.HDAccountPlaceHolder)) {
            contentText = PrimerApplication.mContext.getString(R.string.address_group_hd);
        } else if (Utils.compareString(address, HDAccount.HDAccountMonitoredPlaceHolder)) {
            contentText = PrimerApplication.mContext.getString(R.string.address_group_hd_monitored);
        }
        String title = UnitUtilWrapper.formatValue(amount) + " " + AppSharedPreference.getInstance().getBitcoinUnit().name();
        if (isReceived) {
            title = blockchainService.getString(R.string.feed_received_btc) + " " + title;
        } else {
            title = blockchainService.getString(R.string.feed_send_btc) + " " + title;
        }
        Intent intent = new Intent(blockchainService, HotActivity.class);
        intent.putExtra(PrimerSetting.INTENT_REF.NOTIFICATION_ADDRESS, address);
        SystemUtil.nmNotifyOfWallet(nm, blockchainService,

                PrimerSetting.NOTIFICATION_ID_COINS_RECEIVED, intent, title,
                contentText, R.drawable.icon, R.raw.coins_received);

    }
}
