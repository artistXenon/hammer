/*
 *  Copyright 2016 Lipi C.H. Lee
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/
package pro.jaewon.hammer;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Process;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class MainActivity extends AppCompatActivity implements ActivityResultCallback<ActivityResult>{
	private ActivityResultLauncher<Intent> startActivityIntent;
	private SwitchCompat mProxyStart;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		mProxyStart = findViewById(R.id.startProxy);

		startActivityIntent = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), this);

		if (isVpnRunning()) {
			mProxyStart.setChecked(true);
		}
		mProxyStart.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) startVPN();
			else stopVPN();
        });
	}

	@Override
	protected void onResume() {
		super.onResume();
		mProxyStart.setChecked(isVpnRunning());
	}

	@Override
	public void onActivityResult(ActivityResult result) {
		int resultCode = result.getResultCode();
		if (resultCode == RESULT_OK) {
			startVPN();
		}
	}

	private void startVPN() {
		Intent vpnIntent = VpnService.prepare(this);

		if (vpnIntent != null) {
			//Prepare to establish a VPN connection.
			// This method returns null if the VPN application is already prepared or if the user has previously consented to the VPN application.
			// Otherwise, it returns an Intent to a system activity.
			startActivityIntent.launch(vpnIntent);
			return;
		}
		Intent intent = new Intent(getApplicationContext(), HammerVPNService.class);
		// waitingForVPNStart = true;
		startService(intent);
	}
	private void stopVPN() {
		Intent intent = new Intent("stop_kill");
		LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
	}


	private boolean isVpnRunning() {
		ConnectivityManager cm = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
		Network[] networks = cm.getAllNetworks();
		for (Network n : networks) {
			NetworkCapabilities c = cm.getNetworkCapabilities(n);
			if (c == null || !c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue;
			if (c.getOwnerUid() == Process.myUid()) return true;
		}
		return false;
	}
}
