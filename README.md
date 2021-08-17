pretixPRINT
===========

Android app that drives the printers used by other pretix applications. This contains all
print logic, but no application logic. It is called by other applications using system
broadcasts.

Building
--------

This app comes in two flavors, ``foss`` and ``full``. ``foss`` only supports printers implemented
fully using free software and can be simply built using

	./gradlew assembleFossDebug

If you want to perform a full build, you need to do get a few libraries first:

* Download the [Zebra LinkOS SDK](https://www.zebra.com/gb/en/products/software/barcode-printers/link-os/link-os-sdk.html),
  install it, and extract the three Java archives ``ZSDK_ANDROID_API.jar``, ``ZSDK_CARD_ANDROID_API.jar``, and ``snmp6_1z.jar``
  into the ``ZSDK/`` folder.

* Download the [EPSON ePOS SDK](https://download.epson-biz.com/modules/pos/index.php?page=soft&scat=61) and extract the
  ``ePOS2.jar`` into the ``ePOS/`` folder. Then, add the architecture folders (``armeabi`` etc.) to ``app/src/full/jniLibs/``.

Then, execute:

	./gradlew assembleFullDebug
