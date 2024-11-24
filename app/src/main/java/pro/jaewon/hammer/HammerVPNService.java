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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.VpnService;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.Toast;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import pro.jaewon.hammer.socket.IProtectSocket;
import pro.jaewon.hammer.socket.SocketNIODataService;
import pro.jaewon.hammer.socket.SocketProtector;
import pro.jaewon.hammer.transport.tcp.PacketHeaderException;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class HammerVPNService extends VpnService implements Handler.Callback, Runnable, IProtectSocket {
	private static final String TAG = "HammerVPNService";
	private static final int MAX_PACKET_LEN = 1500;

	private boolean serviceValid;
	private Handler mHandler;
	private Thread mThread;
	private Thread dataServiceThread;
	private ParcelFileDescriptor mInterface;
	private SocketNIODataService dataService;

	private final BroadcastReceiver stopBr = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if ("stop_kill".equals(intent.getAction())) {
				onDestroy();
				stopSelf();
			}
		}
	};

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
		lbm.registerReceiver(stopBr, new IntentFilter("stop_kill"));

		Log.d(TAG, "onStartCommand");
		if (intent == null) {
			return START_STICKY;
		}

		// The handler is only used to show messages.
		if (mHandler == null) {
			mHandler = new Handler(Looper.getMainLooper(), this);
		}

		// Stop the previous session by interrupting the thread.
		if (mThread != null) {
			mThread.interrupt();
			int reps = 0;
			while (mThread.isAlive()) {
				Log.i(TAG, "Waiting to exit " + ++reps);
				try { Thread.sleep(1000); }
				catch (InterruptedException ignore) {}
			}
		}

		// Start a new session by creating a new thread.
		mThread = new Thread(this, "CaptureThread");
		mThread.start();
		return START_STICKY;
	}

	@Override
	public boolean stopService(Intent name) {
		serviceValid = false;
		return super.stopService(name);
	}

	@Override
	public boolean handleMessage(Message message) {
		if (message != null) {
			Log.d(TAG, "handleMessage:" + getString(message.what));
			Toast.makeText(this.getApplicationContext(), message.what, Toast.LENGTH_SHORT).show();
		}
		return true;
	}

	@Override
	public void run() {
		Log.i(TAG, "running vpnService");
		SocketProtector protector = SocketProtector.getInstance();
		protector.setProtector(this);

		try {
			if (startVpnService()) {
				startCapture();
				Log.i(TAG, "Capture completed");
			} else {
				Log.e(TAG,"Failed to start VPN Service!");
			}
		} catch (IOException e) {
			Log.e(TAG,e.getMessage());
		}
		Log.i(TAG, "Closing Capture files");
	}

	@Override
	public void onDestroy() {
		Log.i(TAG, "onDestroy()");
		serviceValid = false;

		if (dataService !=  null) dataService.setShutdown(true);
		if (dataServiceThread != null) dataServiceThread.interrupt();

		if (mInterface != null) {
			Log.i(TAG, "mInterface.close()");
			try { mInterface.close(); }
			catch (IOException e) { Log.d(TAG, "mInterface.close():" + e.getMessage()); }
		}

		// Stop the previous session by interrupting the thread.
		if (mThread == null) return;

		mThread.interrupt();
		int reps = 0;
		while (mThread.isAlive() && reps <= 10) {
			Log.i(TAG, "Waiting to exit " + ++reps);
			try { Thread.sleep(500); }
			catch (InterruptedException ignore) {}
		}
		mThread = null;
	}
	/**
	 * setup VPN interface.
	 * @return boolean
	 * @throws IOException
	 */
	boolean startVpnService() throws IOException {
		// If the old interface has exactly the same parameters, use it!
		if (mInterface != null) {
			Log.i(TAG, "Using the previous interface");
			return false;
		}

		Log.i(TAG, "startVpnService => create builder");
		// Configure a builder while parsing the parameters.
		Builder builder = new Builder()
			.addAddress("10.120.0.1", 32)
			.addRoute("0.0.0.0", 0)
			.setSession("Hammer");
		mInterface = builder.establish();

//		if (mInterface != null) {
//			Log.i(TAG, "VPN Established:interface = " + mInterface.getFileDescriptor().toString());
//			return true;
//		} else {
//			Log.d(TAG,"mInterface is null");
//			return false;
//		}
		return mInterface != null;
	}

	/**
	 * start background thread to handle client's socket, handle incoming and outgoing packet from VPN interface
	 * @throws IOException
	 */
	void startCapture() throws IOException {
		Log.i(TAG, "startCapture() :capture starting");

		// Packets to be sent are queued in this input stream.
		FileInputStream clientReader = new FileInputStream(mInterface.getFileDescriptor());

		// Packets received need to be written to this output stream.
		FileOutputStream clientWriter = new FileOutputStream(mInterface.getFileDescriptor());


		// Allocate the buffer for a single packet.
		ByteBuffer packet = ByteBuffer.allocate(MAX_PACKET_LEN);
		IClientPacketWriter clientPacketWriter = new ClientPacketWriterImpl(clientWriter);

		SessionHandler handler = SessionHandler.getInstance();
		handler.setWriter(clientPacketWriter);

		//background task for non-blocking socket
		dataService = new SocketNIODataService(clientPacketWriter);
		dataServiceThread = new Thread(dataService);
		dataServiceThread.start();

		byte[] data;
		int length;
		serviceValid = true;
		while (serviceValid) {
			//read packet from vpn client
			data = packet.array();
			length = clientReader.read(data);
			if (length <= 0) {
				try { Thread.sleep(100); }
				catch (InterruptedException ignore) {}
				continue;
			}

			try {
				packet.limit(length);
				handler.handlePacket(packet);
			} catch (PacketHeaderException e) {
				Log.e(TAG,e.getMessage());
			}
			packet.clear();
		}
		Log.i(TAG, "capture finished: serviceValid = "+serviceValid);
	}
}
