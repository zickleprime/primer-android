/*
 * Copyright 2014 http://Bither.net
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.primer.ui.base.dialog;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.Fragment;
import android.view.View;
import android.widget.LinearLayout;

import org.primer.PrimerApplication;
import org.primer.PrimerSetting;
import org.primer.R;
import org.primer.SignMessageActivity;
import org.primer.activity.hot.AddressDetailActivity;
import org.primer.primerj.PrimerjSettings;
import org.primer.primerj.core.Address;
import org.primer.primerj.core.AddressManager;
import org.primer.primerj.crypto.SecureCharSequence;
import org.primer.primerj.utils.PrivateKeyUtil;
import org.primer.fragment.cold.ColdAddressFragment;
import org.primer.fragment.hot.HotAddressFragment;
import org.primer.preference.AppSharedPreference;
import org.primer.ui.base.DropdownMessage;
import org.primer.ui.base.listener.IDialogPasswordListener;
import org.primer.util.ThreadUtil;

public class DialogAddressWithShowPrivateKey extends CenterDialog implements View
        .OnClickListener, DialogInterface.OnDismissListener, IDialogPasswordListener {
    private DialogFancyQrCode dialogQr;
    private DialogPrivateKeyQrCode dialogPrivateKey;
    private Address address;
    private LinearLayout llOriginQRCode;
    private LinearLayout llSignMessage;
    private Activity activity;
    private DialogAddressAlias.DialogAddressAliasDelegate aliasDelegate;
    private int clickedView;

    public DialogAddressWithShowPrivateKey(Activity context, Address address,
                                           DialogAddressAlias.DialogAddressAliasDelegate
                                                   aliasDelegate) {
        super(context);
        this.activity = context;
        this.address = address;
        this.aliasDelegate = aliasDelegate;
        setOnDismissListener(this);
        setContentView(R.layout.dialog_address_with_show_private_key);
        llOriginQRCode = (LinearLayout) findViewById(R.id.ll_origin_qr_code);
        llSignMessage = (LinearLayout) findViewById(R.id.ll_sign_message);
        findViewById(R.id.tv_view_show_private_key).setOnClickListener(this);
        findViewById(R.id.tv_private_key_qr_code_decrypted).setOnClickListener(this);
        findViewById(R.id.tv_private_key_qr_code_encrypted).setOnClickListener(this);
        findViewById(R.id.tv_private_key_qr_code_bip38).setOnClickListener(this);
        findViewById(R.id.tv_trash_private_key).setOnClickListener(this);
        findViewById(R.id.ll_address_alias).setOnClickListener(this);
        llOriginQRCode.setOnClickListener(this);
        llOriginQRCode.setVisibility(View.GONE);
        if (AppSharedPreference.getInstance().getAppMode() == PrimerjSettings.AppMode.COLD) {
            llSignMessage.setVisibility(View.VISIBLE);
            llSignMessage.setOnClickListener(this);
        } else {
            llSignMessage.setVisibility(View.GONE);
        }
        findViewById(R.id.tv_close).setOnClickListener(this);
        dialogQr = new DialogFancyQrCode(context, address.getAddress(), false, true);
        dialogPrivateKey = new DialogPrivateKeyQrCode(context, address.getFullEncryptPrivKey(),
                address.getAddress());
        if (aliasDelegate == null) {
            findViewById(R.id.ll_address_alias).setVisibility(View.GONE);
        }
    }

    @Override
    public void show() {
        clickedView = 0;
        super.show();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        switch (clickedView) {
            case R.id.ll_origin_qr_code:
                dialogQr.show();
                break;
            case R.id.tv_view_show_private_key:
                new DialogPassword(activity, this).show();
                break;
            case R.id.tv_private_key_qr_code_encrypted:
                new DialogPassword(activity, this).show();
                break;
            case R.id.tv_private_key_qr_code_decrypted:
                new DialogPassword(activity, this).show();
                break;
            case R.id.tv_private_key_qr_code_bip38:
                new DialogPassword(activity, this).show();
                break;
            case R.id.tv_trash_private_key:
                if (address.getBalance() > 0) {
                    new DialogConfirmTask(getContext(), getContext().getString(R.string
                            .trash_with_money_warn), null).show();
                } else {
                    new DialogPassword(activity, this).show();
                }
                break;
            case R.id.ll_sign_message:
                Intent intent = new Intent(activity, SignMessageActivity.class);
                intent.putExtra(SignMessageActivity.AddressKey, address.getAddress());
                activity.startActivity(intent);
                break;
            case R.id.ll_address_alias:
                new DialogAddressAlias(getContext(), address, aliasDelegate).show();
                break;
            default:
                return;
        }
    }

    @Override
    public void onClick(View v) {
        clickedView = v.getId();
        dismiss();
    }


    @Override
    public void onPasswordEntered(final SecureCharSequence password) {
        final DialogProgress dialogProgress;
        switch (clickedView) {
            case R.id.tv_private_key_qr_code_encrypted:
                password.wipe();
                dialogPrivateKey.show();
                break;
            case R.id.tv_view_show_private_key:
                dialogProgress = new DialogProgress(this.activity, R.string.please_wait);
                dialogProgress.show();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final SecureCharSequence str = PrivateKeyUtil.getDecryptPrivateKeyString(address.getFullEncryptPrivKey(), password);
                        password.wipe();
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                dialogProgress.dismiss();
                                if (str != null) {
                                    DialogPrivateKeyText dialogPrivateKeyText = new DialogPrivateKeyText(activity, str);
                                    dialogPrivateKeyText.show();
                                } else {
                                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                                        @Override
                                        public void run() {
                                            dialogProgress.dismiss();
                                            DropdownMessage.showDropdownMessage(activity, R.string.decrypt_failed);
                                        }
                                    });
                                }
                            }
                        });

                    }

                }).start();

                break;
            case R.id.tv_private_key_qr_code_decrypted:
                dialogProgress = new DialogProgress(this.activity, R.string.please_wait);
                dialogProgress.show();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final SecureCharSequence str = PrivateKeyUtil.getDecryptPrivateKeyString(address.getFullEncryptPrivKey(), password);
                        password.wipe();
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                dialogProgress.dismiss();
                                if (str != null) {
                                    DialogPrivateKeyTextQrCode dialogPrivateKeyTextQrCode = new DialogPrivateKeyTextQrCode(activity, str.toString(), address.getAddress());
                                    dialogPrivateKeyTextQrCode.show();
                                } else {
                                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                                        @Override
                                        public void run() {
                                            dialogProgress.dismiss();
                                            DropdownMessage.showDropdownMessage(activity, R.string.decrypt_failed);
                                        }
                                    });
                                }
                            }
                        });
                    }
                }).start();
                break;
            case R.id.tv_private_key_qr_code_bip38:
                dialogProgress = new DialogProgress(this.activity, R.string.please_wait);
                dialogProgress.show();
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            final String BIP38 = PrivateKeyUtil.getBIP38PrivateKeyString(address,
                                    password);
                            password.wipe();
                            ThreadUtil.runOnMainThread(new Runnable() {
                                @Override
                                public void run() {
                                    dialogProgress.dismiss();
                                    new DialogPrivateKeyQrCode(activity, BIP38,
                                            PrimerSetting.QRCodeType.Bip38,
                                            address.getAddress()).show();
                                }
                            });
                        } catch (Exception e) {
                            e.printStackTrace();
                            dialogProgress.dismiss();
                        }
                    }
                }.start();
                break;
            case R.id.tv_trash_private_key:
                final DialogProgress dp = new DialogProgress(getContext(),
                        R.string.trashing_private_key, null);
                dp.show();
                new Thread() {
                    @Override
                    public void run() {
                        AddressManager.getInstance().trashPrivKey(address);
                        ThreadUtil.runOnMainThread(new Runnable() {
                            @Override
                            public void run() {
                                dp.dismiss();
                                if (activity instanceof AddressDetailActivity) {
                                    activity.finish();
                                }
                                if (AppSharedPreference.getInstance().getAppMode() ==
                                        PrimerjSettings.AppMode.HOT) {
                                    Fragment f = PrimerApplication.hotActivity.getFragmentAtIndex
                                            (1);
                                    if (f instanceof HotAddressFragment) {
                                        HotAddressFragment hotAddressFragment =
                                                (HotAddressFragment) f;
                                        hotAddressFragment.refresh();
                                    }
                                } else {
                                    Fragment f = PrimerApplication.coldActivity
                                            .getFragmentAtIndex(1);
                                    if (f instanceof ColdAddressFragment) {
                                        ColdAddressFragment coldAddressFragment =
                                                (ColdAddressFragment) f;
                                        coldAddressFragment.refresh();
                                    }
                                }
                            }
                        });
                    }
                }.start();
                break;
        }
    }
}
