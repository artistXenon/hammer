package pro.jaewon.hammer;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public enum PacketManager {
    INSTANCE;

    public static final int PACKET = 0;
    @NonNull private final List<Packet> list = new ArrayList<>();
    @NonNull private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (msg != null) {
                if (msg.what == PacketManager.PACKET) {
                }
            }
            super.handleMessage(msg);
        }
    };

    public boolean add(@NonNull final Packet packet) {
        return list.add(packet);
    }

    @NonNull public List<Packet> getList() {
        return list;
    }


    @NonNull public Handler getHandler() {
        return handler;
    }
}
