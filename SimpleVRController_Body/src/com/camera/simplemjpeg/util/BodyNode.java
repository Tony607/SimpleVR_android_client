package com.camera.simplemjpeg.util;

import android.util.Log;

public class BodyNode {
	/** indicated the node is calibrated once since boot */
	private static boolean isCalibrated = false;
	private static final String TAG = "BodyNode";
	/** quaternion read from body tracking node */
	private Quaternion rawQuaternion;
	/** quaternion aligned with user body */
	private Quaternion alignedQuaternion;
	/** quaternion initial aligned with user body */
	private Quaternion initialQuaternion;
	/** Reversed quaternion of initial aligned with user body */
	private Quaternion initialQuaternionReversed;
	/**
	 * Last received xyzw bytes array to compare the change, only effective for
	 * body tracker nodes
	 */
	private byte[] lastXYZWBytes;
	/**
	 * variable indicating if the new change is not send
	 * yet(getBytesArrayFromAlignedQuaternion is not called since the chanced
	 * data arrived), only effective for body tracker nodes
	 */
	private boolean unReadChange = false;
	/** Indicator of head node */
	private boolean isHeadNode;

	public BodyNode(boolean headnode) {
		isHeadNode = headnode;
		rawQuaternion = new Quaternion();
		alignedQuaternion = new Quaternion();
		initialQuaternion = new Quaternion();
		initialQuaternionReversed = new Quaternion();
		lastXYZWBytes = new byte[4];
	}

	/**
	 * set value by the raw bytes from the Bluetooth serial port x,y,z,w -1~1 is
	 * mapped to 0~254. Return unReadChange
	 */
	public boolean setValueByBytes(byte x, byte y, byte z, byte w) {
		// normalize the quaternion first
		byte[] newBytes = new byte[4];
		newBytes[0] = x;
		newBytes[1] = y;
		newBytes[2] = z;
		newBytes[3] = w;

		if (checkChange(newBytes)) {
			float[] q = new float[4];
			for (int i = 0; i < q.length; i++) {
				q[i] = (float) (newBytes[i] & 0xFF) / 127f - 1f;
			}

			float mag = (float) Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2]
					* q[2] + q[3] * q[3]);
			if (mag > 0.0f) {
				rawQuaternion.setValue(q[0] / mag, q[1] / mag, q[2] / mag, q[3]
						/ mag);
			} else {
				rawQuaternion.setValue(0, 0, 0, 1f);
			}
			// do the initial calibration
			if (!isCalibrated) {
				setRawAsInitial();
			}
			calculateAlignedQuaternion();
		}
		return unReadChange;
	}

	/**
	 * function to check if the quaternion data is different since last time,
	 * set unReadChange if change is detected
	 */
	private boolean checkChange(byte[] newBytes) {
		for (int i = 0; i < lastXYZWBytes.length; i++) {
			if (lastXYZWBytes[i] != newBytes[i]) {
				unReadChange = true;
				return true;
			}
		}
		return false;
	}

	/** set current body tracking node quaternion as the initial quaternion. */
	public void setRawAsInitial() {
		initialQuaternion.set(rawQuaternion);
		initialQuaternionReversed.set(initialQuaternion);
		initialQuaternionReversed.invert();
		if (LoggerConfig.ON) {
			Log.d(TAG, "setRawAsInitial()");
		}
		isCalibrated = true;
		calculateAlignedQuaternion();
	}

	/**
	 * calculate and update the aligned quaternion by multiplying it by the
	 * inverse of the initial quaternion. <br/>
	 * ie, Aligned = TnitialReversed x Raw
	 */
	public void calculateAlignedQuaternion() {
		Quaternion tempQuat;
//		if (isHeadNode) {
//			tempQuat = Quaternion.multiply(
//					initialQuaternionReversed, rawQuaternion);
//		} else {
//			tempQuat = Quaternion.multiply(rawQuaternion,
//					initialQuaternionReversed);
//		}
		tempQuat = Quaternion.multiply(
				initialQuaternionReversed, rawQuaternion);
		alignedQuaternion.set(tempQuat);

	}

	public void logTest(String quaternionName) {
		alignedQuaternion.logTest(quaternionName);
	}

	/** set raw quaternion x,y,z,w from a float array */
	public void setRawQuaternion(float[] quaternion) {
		rawQuaternion.setValue(quaternion);
		// do the initial calibration
		if (!isCalibrated) {
			setRawAsInitial();
		}
		calculateAlignedQuaternion();
	}

	public String getJSONStringFromAlignedQuaternion() {
		String string = String.format(
				"{\"x\":%.3f,\"y\":%.3f,\"z\":%.3f,\"w\":%.3f}",
				alignedQuaternion.x, alignedQuaternion.y, alignedQuaternion.z,
				alignedQuaternion.w);
		return string;
	}

	/**
	 * generate the bytes array(length of 4) from the Aligned Quaternion,
	 * x,y,z,w; clear unReadChange bit
	 */
	public byte[] getBytesArrayFromAlignedQuaternion() {
		byte[] bytesArray = new byte[4];
		bytesArray[0] = (byte) ((alignedQuaternion.x + 1) * 127);
		bytesArray[1] = (byte) ((alignedQuaternion.y + 1) * 127);
		bytesArray[2] = (byte) ((alignedQuaternion.z + 1) * 127);
		bytesArray[3] = (byte) ((alignedQuaternion.w + 1) * 127);
		unReadChange = false;
		return bytesArray;
	}
}
