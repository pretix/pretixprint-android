package com.neostra.interfaces;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

public interface INeostraInterfaces extends IInterface {

    String getCashboxStatus() throws RemoteException;
    void openCashbox() throws RemoteException;

    public static class Default implements INeostraInterfaces {
        @Override
        public void openCashbox() throws RemoteException {
        }

        @Override
        public String getCashboxStatus() throws RemoteException {
            return null;
        }

        @Override
        public IBinder asBinder() {
            return null;
        }
    }

    public static abstract class Stub extends Binder implements INeostraInterfaces {
        private static final String DESCRIPTOR = "com.neostra.interfaces.INeostraInterfaces";
        static final int TRANSACTION_getCashboxStatus = 9;
        static final int TRANSACTION_openCashbox = 8;

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static INeostraInterfaces asInterface(IBinder obj) {
            if (obj == null) {
                return null;
            }
            IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (iin != null && (iin instanceof INeostraInterfaces)) {
                return (INeostraInterfaces) iin;
            }
            return new Proxy(obj);
        }

        @Override
        public IBinder asBinder() {
            return this;
        }

        @Override
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            boolean _arg0;
            if (code == 1598968902) {
                reply.writeString(DESCRIPTOR);
                return true;
            }
            switch (code) {
                case TRANSACTION_openCashbox:
                    data.enforceInterface(DESCRIPTOR);
                    openCashbox();
                    reply.writeNoException();
                    return true;
                case TRANSACTION_getCashboxStatus:
                    data.enforceInterface(DESCRIPTOR);
                    String _result5 = getCashboxStatus();
                    reply.writeNoException();
                    reply.writeString(_result5);
                    return true;
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }

        public static class Proxy implements INeostraInterfaces {
            public static INeostraInterfaces sDefaultImpl;
            private IBinder mRemote;

            Proxy(IBinder remote) {
                this.mRemote = remote;
            }

            @Override
            public IBinder asBinder() {
                return this.mRemote;
            }

            public String getInterfaceDescriptor() {
                return Stub.DESCRIPTOR;
            }

            @Override
            public void openCashbox() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    boolean _status = this.mRemote.transact(TRANSACTION_openCashbox, _data, _reply, 0);
                    if (!_status && Stub.getDefaultImpl() != null) {
                        Stub.getDefaultImpl().openCashbox();
                    } else {
                        _reply.readException();
                    }
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override
            public String getCashboxStatus() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    boolean _status = this.mRemote.transact(TRANSACTION_getCashboxStatus, _data, _reply, 0);
                    if (!_status && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().getCashboxStatus();
                    }
                    _reply.readException();
                    String _result = _reply.readString();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }
        }

        public static boolean setDefaultImpl(INeostraInterfaces impl) {
            if (Proxy.sDefaultImpl != null) {
                throw new IllegalStateException("setDefaultImpl() called twice");
            }
            if (impl != null) {
                Proxy.sDefaultImpl = impl;
                return true;
            }
            return false;
        }

        public static INeostraInterfaces getDefaultImpl() {
            return Proxy.sDefaultImpl;
        }
    }
}