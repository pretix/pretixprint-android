package com.neostra.interfaces;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

public interface INeostraInterfaces extends IInterface {
    void closeSerialPort() throws RemoteException;

    boolean copyFolder(String str, String str2) throws RemoteException;

    void disableAppStart(String str, boolean z) throws RemoteException;

    void disableInstall(boolean z) throws RemoteException;

    void disableNetwork(boolean z) throws RemoteException;

    String execShellCmd(String str) throws RemoteException;

    String getCameraBackSize() throws RemoteException;

    String getCameraFrontSize() throws RemoteException;

    String getCashboxStatus() throws RemoteException;

    String getCpuCore() throws RemoteException;

    String getCpuGhz() throws RemoteException;

    String getCpuSoa() throws RemoteException;

    String getDisableAppList() throws RemoteException;

    String getHardwareInfo() throws RemoteException;

    String getPrinterIOStatus() throws RemoteException;

    String getPsamFuncStatus() throws RemoteException;

    String getScreenHwSize() throws RemoteException;

    String getSn() throws RemoteException;

    String getSpiLcmDisplayStatus() throws RemoteException;

    String getSpiLcmPowerStatus() throws RemoteException;

    String getTouchScreenAwakeStatus() throws RemoteException;

    String getUsbHubResetStatus() throws RemoteException;

    String getUsbPrinterPowerStatus() throws RemoteException;

    String getUsbTypeStatus() throws RemoteException;

    String getWifiIpAddres() throws RemoteException;

    void httListLimit(String str, String str2) throws RemoteException;

    void openCashbox() throws RemoteException;

    void openSerialPort() throws RemoteException;

    String readhttListLimit() throws RemoteException;

    void removePeel() throws RemoteException;

    void setPsamFuncStatus(String str) throws RemoteException;

    void setRemoteAdb(boolean z) throws RemoteException;

    void setSpiLcmPower(String str) throws RemoteException;

    void setTouchScreenAwake(String str) throws RemoteException;

    void setUsbHubReset(String str) throws RemoteException;

    void setUsbPrinterPower(String str) throws RemoteException;

    void setUsbTypeStatus(String str) throws RemoteException;

    String testSPI(String str) throws RemoteException;

    void turnZero() throws RemoteException;

    public static class Default implements INeostraInterfaces {
        @Override // com.neostra.interfaces.INeostraInterfaces
        public String getHardwareInfo() throws RemoteException {
            return null;
        }

        @Override // com.neostra.interfaces.INeostraInterfaces
        public void setUsbTypeStatus(String status) throws RemoteException {
        }

        @Override // com.neostra.interfaces.INeostraInterfaces
        public String getUsbTypeStatus() throws RemoteException {
            return null;
        }

        @Override // com.neostra.interfaces.INeostraInterfaces
        public void setUsbPrinterPower(String status) throws RemoteException {
        }

        @Override // com.neostra.interfaces.INeostraInterfaces
        public String getUsbPrinterPowerStatus() throws RemoteException {
            return null;
        }

        @Override // com.neostra.interfaces.INeostraInterfaces
        public void setUsbHubReset(String status) throws RemoteException {
        }

        @Override // com.neostra.interfaces.INeostraInterfaces
        public String getUsbHubResetStatus() throws RemoteException {
            return null;
        }

        @Override // com.neostra.interfaces.INeostraInterfaces
        public void openCashbox() throws RemoteException {
        }

        @Override // com.neostra.interfaces.INeostraInterfaces
        public String getCashboxStatus() throws RemoteException {
            return null;
        }

        @Override // com.neostra.interfaces.INeostraInterfaces
        public void setSpiLcmPower(String status) throws RemoteException {
        }

        @Override // com.neostra.interfaces.INeostraInterfaces
        public String getSpiLcmPowerStatus() throws RemoteException {
            return null;
        }

        @Override // com.neostra.interfaces.INeostraInterfaces
        public String getSpiLcmDisplayStatus() throws RemoteException {
            return null;
        }

        @Override // com.neostra.interfaces.INeostraInterfaces
        public void setTouchScreenAwake(String status) throws RemoteException {
        }

        @Override // com.neostra.interfaces.INeostraInterfaces
        public String getTouchScreenAwakeStatus() throws RemoteException {
            return null;
        }

        @Override // com.neostra.interfaces.INeostraInterfaces
        public String getPrinterIOStatus() throws RemoteException {
            return null;
        }

        @Override // com.neostra.interfaces.INeostraInterfaces
        public String execShellCmd(String cmd) throws RemoteException {
            return null;
        }

        @Override // com.neostra.interfaces.INeostraInterfaces
        public String getSn() throws RemoteException {
            return null;
        }

        @Override // com.neostra.interfaces.INeostraInterfaces
        public String getCpuCore() throws RemoteException {
            return null;
        }

        @Override // com.neostra.interfaces.INeostraInterfaces
        public String getCpuGhz() throws RemoteException {
            return null;
        }

        @Override // com.neostra.interfaces.INeostraInterfaces
        public String getCpuSoa() throws RemoteException {
            return null;
        }

        @Override // com.neostra.interfaces.INeostraInterfaces
        public String getScreenHwSize() throws RemoteException {
            return null;
        }

        @Override // com.neostra.interfaces.INeostraInterfaces
        public String getCameraFrontSize() throws RemoteException {
            return null;
        }

        @Override // com.neostra.interfaces.INeostraInterfaces
        public String getCameraBackSize() throws RemoteException {
            return null;
        }

        @Override // com.neostra.interfaces.INeostraInterfaces
        public String testSPI(String text) throws RemoteException {
            return null;
        }

        @Override // com.neostra.interfaces.INeostraInterfaces
        public boolean copyFolder(String oldPath, String newPath) throws RemoteException {
            return false;
        }

        @Override // com.neostra.interfaces.INeostraInterfaces
        public void setRemoteAdb(boolean isOpen) throws RemoteException {
        }

        @Override // com.neostra.interfaces.INeostraInterfaces
        public String getWifiIpAddres() throws RemoteException {
            return null;
        }

        @Override // com.neostra.interfaces.INeostraInterfaces
        public void disableNetwork(boolean enable) throws RemoteException {
        }

        @Override // com.neostra.interfaces.INeostraInterfaces
        public void httListLimit(String limitType, String limitList) throws RemoteException {
        }

        @Override // com.neostra.interfaces.INeostraInterfaces
        public String readhttListLimit() throws RemoteException {
            return null;
        }

        @Override // com.neostra.interfaces.INeostraInterfaces
        public void disableAppStart(String packageName, boolean enable) throws RemoteException {
        }

        @Override // com.neostra.interfaces.INeostraInterfaces
        public String getDisableAppList() throws RemoteException {
            return null;
        }

        @Override // com.neostra.interfaces.INeostraInterfaces
        public void disableInstall(boolean enable) throws RemoteException {
        }

        @Override // com.neostra.interfaces.INeostraInterfaces
        public void openSerialPort() throws RemoteException {
        }

        @Override // com.neostra.interfaces.INeostraInterfaces
        public void turnZero() throws RemoteException {
        }

        @Override // com.neostra.interfaces.INeostraInterfaces
        public void removePeel() throws RemoteException {
        }

        @Override // com.neostra.interfaces.INeostraInterfaces
        public void closeSerialPort() throws RemoteException {
        }

        @Override // com.neostra.interfaces.INeostraInterfaces
        public void setPsamFuncStatus(String status) throws RemoteException {
        }

        @Override // com.neostra.interfaces.INeostraInterfaces
        public String getPsamFuncStatus() throws RemoteException {
            return null;
        }

        @Override // android.os.IInterface
        public IBinder asBinder() {
            return null;
        }
    }

    /* loaded from: classes2.dex */
    public static abstract class Stub extends Binder implements INeostraInterfaces {
        private static final String DESCRIPTOR = "com.neostra.interfaces.INeostraInterfaces";
        static final int TRANSACTION_closeSerialPort = 37;
        static final int TRANSACTION_copyFolder = 25;
        static final int TRANSACTION_disableAppStart = 31;
        static final int TRANSACTION_disableInstall = 33;
        static final int TRANSACTION_disableNetwork = 28;
        static final int TRANSACTION_execShellCmd = 16;
        static final int TRANSACTION_getCameraBackSize = 23;
        static final int TRANSACTION_getCameraFrontSize = 22;
        static final int TRANSACTION_getCashboxStatus = 9;
        static final int TRANSACTION_getCpuCore = 18;
        static final int TRANSACTION_getCpuGhz = 19;
        static final int TRANSACTION_getCpuSoa = 20;
        static final int TRANSACTION_getDisableAppList = 32;
        static final int TRANSACTION_getHardwareInfo = 1;
        static final int TRANSACTION_getPrinterIOStatus = 15;
        static final int TRANSACTION_getPsamFuncStatus = 39;
        static final int TRANSACTION_getScreenHwSize = 21;
        static final int TRANSACTION_getSn = 17;
        static final int TRANSACTION_getSpiLcmDisplayStatus = 12;
        static final int TRANSACTION_getSpiLcmPowerStatus = 11;
        static final int TRANSACTION_getTouchScreenAwakeStatus = 14;
        static final int TRANSACTION_getUsbHubResetStatus = 7;
        static final int TRANSACTION_getUsbPrinterPowerStatus = 5;
        static final int TRANSACTION_getUsbTypeStatus = 3;
        static final int TRANSACTION_getWifiIpAddres = 27;
        static final int TRANSACTION_httListLimit = 29;
        static final int TRANSACTION_openCashbox = 8;
        static final int TRANSACTION_openSerialPort = 34;
        static final int TRANSACTION_readhttListLimit = 30;
        static final int TRANSACTION_removePeel = 36;
        static final int TRANSACTION_setPsamFuncStatus = 38;
        static final int TRANSACTION_setRemoteAdb = 26;
        static final int TRANSACTION_setSpiLcmPower = 10;
        static final int TRANSACTION_setTouchScreenAwake = 13;
        static final int TRANSACTION_setUsbHubReset = 6;
        static final int TRANSACTION_setUsbPrinterPower = 4;
        static final int TRANSACTION_setUsbTypeStatus = 2;
        static final int TRANSACTION_testSPI = 24;
        static final int TRANSACTION_turnZero = 35;

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

        @Override // android.os.IInterface
        public IBinder asBinder() {
            return this;
        }

        @Override // android.os.Binder
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            boolean _arg0;
            if (code == 1598968902) {
                reply.writeString(DESCRIPTOR);
                return true;
            }
            switch (code) {
                case 1:
                    data.enforceInterface(DESCRIPTOR);
                    String _result = getHardwareInfo();
                    reply.writeNoException();
                    reply.writeString(_result);
                    return true;
                case 2:
                    data.enforceInterface(DESCRIPTOR);
                    setUsbTypeStatus(data.readString());
                    reply.writeNoException();
                    return true;
                case 3:
                    data.enforceInterface(DESCRIPTOR);
                    String _result2 = getUsbTypeStatus();
                    reply.writeNoException();
                    reply.writeString(_result2);
                    return true;
                case 4:
                    data.enforceInterface(DESCRIPTOR);
                    setUsbPrinterPower(data.readString());
                    reply.writeNoException();
                    return true;
                case 5:
                    data.enforceInterface(DESCRIPTOR);
                    String _result3 = getUsbPrinterPowerStatus();
                    reply.writeNoException();
                    reply.writeString(_result3);
                    return true;
                case 6:
                    data.enforceInterface(DESCRIPTOR);
                    setUsbHubReset(data.readString());
                    reply.writeNoException();
                    return true;
                case 7:
                    data.enforceInterface(DESCRIPTOR);
                    String _result4 = getUsbHubResetStatus();
                    reply.writeNoException();
                    reply.writeString(_result4);
                    return true;
                case 8:
                    data.enforceInterface(DESCRIPTOR);
                    openCashbox();
                    reply.writeNoException();
                    return true;
                case 9:
                    data.enforceInterface(DESCRIPTOR);
                    String _result5 = getCashboxStatus();
                    reply.writeNoException();
                    reply.writeString(_result5);
                    return true;
                case 10:
                    data.enforceInterface(DESCRIPTOR);
                    setSpiLcmPower(data.readString());
                    reply.writeNoException();
                    return true;
                case 11:
                    data.enforceInterface(DESCRIPTOR);
                    String _result6 = getSpiLcmPowerStatus();
                    reply.writeNoException();
                    reply.writeString(_result6);
                    return true;
                case 12:
                    data.enforceInterface(DESCRIPTOR);
                    String _result7 = getSpiLcmDisplayStatus();
                    reply.writeNoException();
                    reply.writeString(_result7);
                    return true;
                case 13:
                    data.enforceInterface(DESCRIPTOR);
                    setTouchScreenAwake(data.readString());
                    reply.writeNoException();
                    return true;
                case 14:
                    data.enforceInterface(DESCRIPTOR);
                    String _result8 = getTouchScreenAwakeStatus();
                    reply.writeNoException();
                    reply.writeString(_result8);
                    return true;
                case 15:
                    data.enforceInterface(DESCRIPTOR);
                    String _result9 = getPrinterIOStatus();
                    reply.writeNoException();
                    reply.writeString(_result9);
                    return true;
                case 16:
                    data.enforceInterface(DESCRIPTOR);
                    String _result10 = execShellCmd(data.readString());
                    reply.writeNoException();
                    reply.writeString(_result10);
                    return true;
                case 17:
                    data.enforceInterface(DESCRIPTOR);
                    String _result11 = getSn();
                    reply.writeNoException();
                    reply.writeString(_result11);
                    return true;
                case 18:
                    data.enforceInterface(DESCRIPTOR);
                    String _result12 = getCpuCore();
                    reply.writeNoException();
                    reply.writeString(_result12);
                    return true;
                case 19:
                    data.enforceInterface(DESCRIPTOR);
                    String _result13 = getCpuGhz();
                    reply.writeNoException();
                    reply.writeString(_result13);
                    return true;
                case 20:
                    data.enforceInterface(DESCRIPTOR);
                    String _result14 = getCpuSoa();
                    reply.writeNoException();
                    reply.writeString(_result14);
                    return true;
                case 21:
                    data.enforceInterface(DESCRIPTOR);
                    String _result15 = getScreenHwSize();
                    reply.writeNoException();
                    reply.writeString(_result15);
                    return true;
                case 22:
                    data.enforceInterface(DESCRIPTOR);
                    String _result16 = getCameraFrontSize();
                    reply.writeNoException();
                    reply.writeString(_result16);
                    return true;
                case 23:
                    data.enforceInterface(DESCRIPTOR);
                    String _result17 = getCameraBackSize();
                    reply.writeNoException();
                    reply.writeString(_result17);
                    return true;
                case 24:
                    data.enforceInterface(DESCRIPTOR);
                    String _result18 = testSPI(data.readString());
                    reply.writeNoException();
                    reply.writeString(_result18);
                    return true;
                case 25:
                    data.enforceInterface(DESCRIPTOR);
                    String _arg02 = data.readString();
                    String _arg1 = data.readString();
                    boolean copyFolder = copyFolder(_arg02, _arg1);
                    reply.writeNoException();
                    reply.writeInt(copyFolder ? 1 : 0);
                    return true;
                case 26:
                    data.enforceInterface(DESCRIPTOR);
                    _arg0 = data.readInt() != 0;
                    setRemoteAdb(_arg0);
                    reply.writeNoException();
                    return true;
                case 27:
                    data.enforceInterface(DESCRIPTOR);
                    String _result19 = getWifiIpAddres();
                    reply.writeNoException();
                    reply.writeString(_result19);
                    return true;
                case 28:
                    data.enforceInterface(DESCRIPTOR);
                    _arg0 = data.readInt() != 0;
                    disableNetwork(_arg0);
                    reply.writeNoException();
                    return true;
                case 29:
                    data.enforceInterface(DESCRIPTOR);
                    String _arg03 = data.readString();
                    String _arg12 = data.readString();
                    httListLimit(_arg03, _arg12);
                    reply.writeNoException();
                    return true;
                case 30:
                    data.enforceInterface(DESCRIPTOR);
                    String _result20 = readhttListLimit();
                    reply.writeNoException();
                    reply.writeString(_result20);
                    return true;
                case 31:
                    data.enforceInterface(DESCRIPTOR);
                    String _arg04 = data.readString();
                    _arg0 = data.readInt() != 0;
                    disableAppStart(_arg04, _arg0);
                    reply.writeNoException();
                    return true;
                case 32:
                    data.enforceInterface(DESCRIPTOR);
                    String _result21 = getDisableAppList();
                    reply.writeNoException();
                    reply.writeString(_result21);
                    return true;
                case 33:
                    data.enforceInterface(DESCRIPTOR);
                    _arg0 = data.readInt() != 0;
                    disableInstall(_arg0);
                    reply.writeNoException();
                    return true;
                case 34:
                    data.enforceInterface(DESCRIPTOR);
                    openSerialPort();
                    reply.writeNoException();
                    return true;
                case 35:
                    data.enforceInterface(DESCRIPTOR);
                    turnZero();
                    reply.writeNoException();
                    return true;
                case 36:
                    data.enforceInterface(DESCRIPTOR);
                    removePeel();
                    reply.writeNoException();
                    return true;
                case 37:
                    data.enforceInterface(DESCRIPTOR);
                    closeSerialPort();
                    reply.writeNoException();
                    return true;
                case 38:
                    data.enforceInterface(DESCRIPTOR);
                    setPsamFuncStatus(data.readString());
                    reply.writeNoException();
                    return true;
                case 39:
                    data.enforceInterface(DESCRIPTOR);
                    String _result22 = getPsamFuncStatus();
                    reply.writeNoException();
                    reply.writeString(_result22);
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

            @Override // android.os.IInterface
            public IBinder asBinder() {
                return this.mRemote;
            }

            public String getInterfaceDescriptor() {
                return Stub.DESCRIPTOR;
            }

            @Override // com.neostra.interfaces.INeostraInterfaces
            public String getHardwareInfo() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    boolean _status = this.mRemote.transact(1, _data, _reply, 0);
                    if (!_status && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().getHardwareInfo();
                    }
                    _reply.readException();
                    String _result = _reply.readString();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.neostra.interfaces.INeostraInterfaces
            public void setUsbTypeStatus(String status) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeString(status);
                    boolean _status = this.mRemote.transact(2, _data, _reply, 0);
                    if (!_status && Stub.getDefaultImpl() != null) {
                        Stub.getDefaultImpl().setUsbTypeStatus(status);
                    } else {
                        _reply.readException();
                    }
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.neostra.interfaces.INeostraInterfaces
            public String getUsbTypeStatus() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    boolean _status = this.mRemote.transact(3, _data, _reply, 0);
                    if (!_status && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().getUsbTypeStatus();
                    }
                    _reply.readException();
                    String _result = _reply.readString();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.neostra.interfaces.INeostraInterfaces
            public void setUsbPrinterPower(String status) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeString(status);
                    boolean _status = this.mRemote.transact(4, _data, _reply, 0);
                    if (!_status && Stub.getDefaultImpl() != null) {
                        Stub.getDefaultImpl().setUsbPrinterPower(status);
                    } else {
                        _reply.readException();
                    }
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.neostra.interfaces.INeostraInterfaces
            public String getUsbPrinterPowerStatus() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    boolean _status = this.mRemote.transact(5, _data, _reply, 0);
                    if (!_status && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().getUsbPrinterPowerStatus();
                    }
                    _reply.readException();
                    String _result = _reply.readString();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.neostra.interfaces.INeostraInterfaces
            public void setUsbHubReset(String status) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeString(status);
                    boolean _status = this.mRemote.transact(6, _data, _reply, 0);
                    if (!_status && Stub.getDefaultImpl() != null) {
                        Stub.getDefaultImpl().setUsbHubReset(status);
                    } else {
                        _reply.readException();
                    }
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.neostra.interfaces.INeostraInterfaces
            public String getUsbHubResetStatus() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    boolean _status = this.mRemote.transact(7, _data, _reply, 0);
                    if (!_status && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().getUsbHubResetStatus();
                    }
                    _reply.readException();
                    String _result = _reply.readString();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.neostra.interfaces.INeostraInterfaces
            public void openCashbox() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    boolean _status = this.mRemote.transact(8, _data, _reply, 0);
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

            @Override // com.neostra.interfaces.INeostraInterfaces
            public String getCashboxStatus() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    boolean _status = this.mRemote.transact(9, _data, _reply, 0);
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

            @Override // com.neostra.interfaces.INeostraInterfaces
            public void setSpiLcmPower(String status) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeString(status);
                    boolean _status = this.mRemote.transact(10, _data, _reply, 0);
                    if (!_status && Stub.getDefaultImpl() != null) {
                        Stub.getDefaultImpl().setSpiLcmPower(status);
                    } else {
                        _reply.readException();
                    }
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.neostra.interfaces.INeostraInterfaces
            public String getSpiLcmPowerStatus() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    boolean _status = this.mRemote.transact(11, _data, _reply, 0);
                    if (!_status && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().getSpiLcmPowerStatus();
                    }
                    _reply.readException();
                    String _result = _reply.readString();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.neostra.interfaces.INeostraInterfaces
            public String getSpiLcmDisplayStatus() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    boolean _status = this.mRemote.transact(12, _data, _reply, 0);
                    if (!_status && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().getSpiLcmDisplayStatus();
                    }
                    _reply.readException();
                    String _result = _reply.readString();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.neostra.interfaces.INeostraInterfaces
            public void setTouchScreenAwake(String status) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeString(status);
                    boolean _status = this.mRemote.transact(13, _data, _reply, 0);
                    if (!_status && Stub.getDefaultImpl() != null) {
                        Stub.getDefaultImpl().setTouchScreenAwake(status);
                    } else {
                        _reply.readException();
                    }
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.neostra.interfaces.INeostraInterfaces
            public String getTouchScreenAwakeStatus() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    boolean _status = this.mRemote.transact(14, _data, _reply, 0);
                    if (!_status && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().getTouchScreenAwakeStatus();
                    }
                    _reply.readException();
                    String _result = _reply.readString();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.neostra.interfaces.INeostraInterfaces
            public String getPrinterIOStatus() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    boolean _status = this.mRemote.transact(15, _data, _reply, 0);
                    if (!_status && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().getPrinterIOStatus();
                    }
                    _reply.readException();
                    String _result = _reply.readString();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.neostra.interfaces.INeostraInterfaces
            public String execShellCmd(String cmd) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeString(cmd);
                    boolean _status = this.mRemote.transact(16, _data, _reply, 0);
                    if (!_status && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().execShellCmd(cmd);
                    }
                    _reply.readException();
                    String _result = _reply.readString();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.neostra.interfaces.INeostraInterfaces
            public String getSn() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    boolean _status = this.mRemote.transact(17, _data, _reply, 0);
                    if (!_status && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().getSn();
                    }
                    _reply.readException();
                    String _result = _reply.readString();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.neostra.interfaces.INeostraInterfaces
            public String getCpuCore() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    boolean _status = this.mRemote.transact(18, _data, _reply, 0);
                    if (!_status && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().getCpuCore();
                    }
                    _reply.readException();
                    String _result = _reply.readString();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.neostra.interfaces.INeostraInterfaces
            public String getCpuGhz() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    boolean _status = this.mRemote.transact(19, _data, _reply, 0);
                    if (!_status && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().getCpuGhz();
                    }
                    _reply.readException();
                    String _result = _reply.readString();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.neostra.interfaces.INeostraInterfaces
            public String getCpuSoa() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    boolean _status = this.mRemote.transact(20, _data, _reply, 0);
                    if (!_status && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().getCpuSoa();
                    }
                    _reply.readException();
                    String _result = _reply.readString();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.neostra.interfaces.INeostraInterfaces
            public String getScreenHwSize() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    boolean _status = this.mRemote.transact(21, _data, _reply, 0);
                    if (!_status && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().getScreenHwSize();
                    }
                    _reply.readException();
                    String _result = _reply.readString();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.neostra.interfaces.INeostraInterfaces
            public String getCameraFrontSize() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    boolean _status = this.mRemote.transact(22, _data, _reply, 0);
                    if (!_status && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().getCameraFrontSize();
                    }
                    _reply.readException();
                    String _result = _reply.readString();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.neostra.interfaces.INeostraInterfaces
            public String getCameraBackSize() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    boolean _status = this.mRemote.transact(23, _data, _reply, 0);
                    if (!_status && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().getCameraBackSize();
                    }
                    _reply.readException();
                    String _result = _reply.readString();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.neostra.interfaces.INeostraInterfaces
            public String testSPI(String text) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeString(text);
                    boolean _status = this.mRemote.transact(24, _data, _reply, 0);
                    if (!_status && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().testSPI(text);
                    }
                    _reply.readException();
                    String _result = _reply.readString();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.neostra.interfaces.INeostraInterfaces
            public boolean copyFolder(String oldPath, String newPath) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeString(oldPath);
                    _data.writeString(newPath);
                    boolean _status = this.mRemote.transact(25, _data, _reply, 0);
                    if (!_status && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().copyFolder(oldPath, newPath);
                    }
                    _reply.readException();
                    boolean _status2 = _reply.readInt() != 0;
                    return _status2;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.neostra.interfaces.INeostraInterfaces
            public void setRemoteAdb(boolean isOpen) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(isOpen ? 1 : 0);
                    boolean _status = this.mRemote.transact(26, _data, _reply, 0);
                    if (!_status && Stub.getDefaultImpl() != null) {
                        Stub.getDefaultImpl().setRemoteAdb(isOpen);
                    } else {
                        _reply.readException();
                    }
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.neostra.interfaces.INeostraInterfaces
            public String getWifiIpAddres() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    boolean _status = this.mRemote.transact(27, _data, _reply, 0);
                    if (!_status && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().getWifiIpAddres();
                    }
                    _reply.readException();
                    String _result = _reply.readString();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.neostra.interfaces.INeostraInterfaces
            public void disableNetwork(boolean enable) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(enable ? 1 : 0);
                    boolean _status = this.mRemote.transact(28, _data, _reply, 0);
                    if (!_status && Stub.getDefaultImpl() != null) {
                        Stub.getDefaultImpl().disableNetwork(enable);
                    } else {
                        _reply.readException();
                    }
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.neostra.interfaces.INeostraInterfaces
            public void httListLimit(String limitType, String limitList) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeString(limitType);
                    _data.writeString(limitList);
                    boolean _status = this.mRemote.transact(29, _data, _reply, 0);
                    if (!_status && Stub.getDefaultImpl() != null) {
                        Stub.getDefaultImpl().httListLimit(limitType, limitList);
                    } else {
                        _reply.readException();
                    }
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.neostra.interfaces.INeostraInterfaces
            public String readhttListLimit() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    boolean _status = this.mRemote.transact(30, _data, _reply, 0);
                    if (!_status && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().readhttListLimit();
                    }
                    _reply.readException();
                    String _result = _reply.readString();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.neostra.interfaces.INeostraInterfaces
            public void disableAppStart(String packageName, boolean enable) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeString(packageName);
                    _data.writeInt(enable ? 1 : 0);
                    boolean _status = this.mRemote.transact(31, _data, _reply, 0);
                    if (!_status && Stub.getDefaultImpl() != null) {
                        Stub.getDefaultImpl().disableAppStart(packageName, enable);
                    } else {
                        _reply.readException();
                    }
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.neostra.interfaces.INeostraInterfaces
            public String getDisableAppList() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    boolean _status = this.mRemote.transact(32, _data, _reply, 0);
                    if (!_status && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().getDisableAppList();
                    }
                    _reply.readException();
                    String _result = _reply.readString();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.neostra.interfaces.INeostraInterfaces
            public void disableInstall(boolean enable) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(enable ? 1 : 0);
                    boolean _status = this.mRemote.transact(33, _data, _reply, 0);
                    if (!_status && Stub.getDefaultImpl() != null) {
                        Stub.getDefaultImpl().disableInstall(enable);
                    } else {
                        _reply.readException();
                    }
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.neostra.interfaces.INeostraInterfaces
            public void openSerialPort() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    boolean _status = this.mRemote.transact(34, _data, _reply, 0);
                    if (!_status && Stub.getDefaultImpl() != null) {
                        Stub.getDefaultImpl().openSerialPort();
                    } else {
                        _reply.readException();
                    }
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.neostra.interfaces.INeostraInterfaces
            public void turnZero() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    boolean _status = this.mRemote.transact(35, _data, _reply, 0);
                    if (!_status && Stub.getDefaultImpl() != null) {
                        Stub.getDefaultImpl().turnZero();
                    } else {
                        _reply.readException();
                    }
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.neostra.interfaces.INeostraInterfaces
            public void removePeel() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    boolean _status = this.mRemote.transact(36, _data, _reply, 0);
                    if (!_status && Stub.getDefaultImpl() != null) {
                        Stub.getDefaultImpl().removePeel();
                    } else {
                        _reply.readException();
                    }
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.neostra.interfaces.INeostraInterfaces
            public void closeSerialPort() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    boolean _status = this.mRemote.transact(37, _data, _reply, 0);
                    if (!_status && Stub.getDefaultImpl() != null) {
                        Stub.getDefaultImpl().closeSerialPort();
                    } else {
                        _reply.readException();
                    }
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.neostra.interfaces.INeostraInterfaces
            public void setPsamFuncStatus(String status) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeString(status);
                    boolean _status = this.mRemote.transact(38, _data, _reply, 0);
                    if (!_status && Stub.getDefaultImpl() != null) {
                        Stub.getDefaultImpl().setPsamFuncStatus(status);
                    } else {
                        _reply.readException();
                    }
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.neostra.interfaces.INeostraInterfaces
            public String getPsamFuncStatus() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    boolean _status = this.mRemote.transact(39, _data, _reply, 0);
                    if (!_status && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().getPsamFuncStatus();
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