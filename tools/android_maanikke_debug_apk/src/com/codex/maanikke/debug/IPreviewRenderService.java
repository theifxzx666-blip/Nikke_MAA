package com.codex.maanikke.debug;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import android.view.Surface;

public interface IPreviewRenderService extends IInterface {
    boolean setSurface(Surface surface) throws RemoteException;

    void clearSurface() throws RemoteException;

    boolean startFilePreview(String framePath, int minIntervalMs) throws RemoteException;

    void stopFilePreview() throws RemoteException;

    String getStatus() throws RemoteException;

    void shutdown() throws RemoteException;

    abstract class Stub extends Binder implements IPreviewRenderService {
        private static final String DESCRIPTOR = "com.codex.maanikke.debug.IPreviewRenderService";
        private static final int TRANSACTION_SET_SURFACE = IBinder.FIRST_CALL_TRANSACTION;
        private static final int TRANSACTION_CLEAR_SURFACE = IBinder.FIRST_CALL_TRANSACTION + 1;
        private static final int TRANSACTION_START_FILE_PREVIEW = IBinder.FIRST_CALL_TRANSACTION + 2;
        private static final int TRANSACTION_STOP_FILE_PREVIEW = IBinder.FIRST_CALL_TRANSACTION + 3;
        private static final int TRANSACTION_GET_STATUS = IBinder.FIRST_CALL_TRANSACTION + 4;
        private static final int TRANSACTION_SHUTDOWN = IBinder.FIRST_CALL_TRANSACTION + 5;

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static IPreviewRenderService asInterface(IBinder binder) {
            if (binder == null) {
                return null;
            }
            IInterface local = binder.queryLocalInterface(DESCRIPTOR);
            if (local instanceof IPreviewRenderService) {
                return (IPreviewRenderService) local;
            }
            return new Proxy(binder);
        }

        @Override
        public IBinder asBinder() {
            return this;
        }

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            switch (code) {
                case INTERFACE_TRANSACTION:
                    reply.writeString(DESCRIPTOR);
                    return true;
                case TRANSACTION_SET_SURFACE: {
                    data.enforceInterface(DESCRIPTOR);
                    Surface surface = data.readInt() != 0 ? Surface.CREATOR.createFromParcel(data) : null;
                    boolean result = setSurface(surface);
                    reply.writeNoException();
                    reply.writeInt(result ? 1 : 0);
                    return true;
                }
                case TRANSACTION_CLEAR_SURFACE:
                    data.enforceInterface(DESCRIPTOR);
                    clearSurface();
                    reply.writeNoException();
                    return true;
                case TRANSACTION_START_FILE_PREVIEW: {
                    data.enforceInterface(DESCRIPTOR);
                    boolean result = startFilePreview(data.readString(), data.readInt());
                    reply.writeNoException();
                    reply.writeInt(result ? 1 : 0);
                    return true;
                }
                case TRANSACTION_STOP_FILE_PREVIEW:
                    data.enforceInterface(DESCRIPTOR);
                    stopFilePreview();
                    reply.writeNoException();
                    return true;
                case TRANSACTION_GET_STATUS: {
                    data.enforceInterface(DESCRIPTOR);
                    String status = getStatus();
                    reply.writeNoException();
                    reply.writeString(status);
                    return true;
                }
                case TRANSACTION_SHUTDOWN:
                    data.enforceInterface(DESCRIPTOR);
                    shutdown();
                    reply.writeNoException();
                    return true;
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }

        private static final class Proxy implements IPreviewRenderService {
            private final IBinder remote;

            Proxy(IBinder remote) {
                this.remote = remote;
            }

            @Override
            public IBinder asBinder() {
                return remote;
            }

            @Override
            public boolean setSurface(Surface surface) throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    if (surface != null) {
                        data.writeInt(1);
                        surface.writeToParcel(data, 0);
                    } else {
                        data.writeInt(0);
                    }
                    remote.transact(TRANSACTION_SET_SURFACE, data, reply, 0);
                    reply.readException();
                    return reply.readInt() != 0;
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public void clearSurface() throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    remote.transact(TRANSACTION_CLEAR_SURFACE, data, reply, 0);
                    reply.readException();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public boolean startFilePreview(String framePath, int minIntervalMs) throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeString(framePath);
                    data.writeInt(minIntervalMs);
                    remote.transact(TRANSACTION_START_FILE_PREVIEW, data, reply, 0);
                    reply.readException();
                    return reply.readInt() != 0;
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public void stopFilePreview() throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    remote.transact(TRANSACTION_STOP_FILE_PREVIEW, data, reply, 0);
                    reply.readException();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public String getStatus() throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    remote.transact(TRANSACTION_GET_STATUS, data, reply, 0);
                    reply.readException();
                    return reply.readString();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public void shutdown() throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    remote.transact(TRANSACTION_SHUTDOWN, data, reply, 0);
                    reply.readException();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }
        }
    }
}
