package com.camera.simplemjpeg.util;

import android.util.Log;

public class Quaternion {
	private static final String DEBUG = "QuaternionModel";
	public float x;
	public float y;
	public float z;
	public float w;

	public Quaternion() {
		x = 0;
		y = 0;
		z = 0;
		w = 1f;
	}
    /**
     * Copy constructor.
     * 
     * @param q1
     *          the Quaternion containing the initialization x y z w data
     */
    public Quaternion(Quaternion q1) {
            set(q1);
    }
	public Quaternion(float x, float y, float z, float w) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.w = w;
	}

    /**
     * Set this Quaternion from quaternion.
     */
	public void set(Quaternion q1) {
		this.x = q1.x;
        this.y = q1.y;
        this.z = q1.z;
        this.w = q1.w;
	}
	public void setValue(float x, float y, float z, float w) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.w = w;
	}
    /**set the quaternion's  x,y,z,w from a float array*/
	public void setValue(float[] quaternion) {
		this.x = quaternion[0];
		this.y = quaternion[1];
		this.z = quaternion[2];
		this.w = quaternion[3];
	}

    /**
     * Normalizes the value of this Quaternion in place and return its {@code
     * norm}.
     */
    public final float normalize() {
            float norm = (float) Math.sqrt(this.x * this.x + this.y * this.y + this.z
                            * this.z + this.w * this.w);
            if (norm > 0.0f) {
                    this.x /= norm;
                    this.y /= norm;
                    this.z /= norm;
                    this.w /= norm;
            } else {
                    this.x = (float) 0.0;
                    this.y = (float) 0.0;
                    this.z = (float) 0.0;
                    this.w = (float) 1.0;
            }
            return norm;
    }

	public void logTest(String quaternionName) {
		if (LoggerConfig.ON) {
			Log.d(DEBUG,String.format("%s:\t%.2f:\t%.2f:\t%.2f:\t%.2f",quaternionName, this.x, this.y, this.z, this.w));
		}
	}

    /**
     * Sets the value of this Quaternion to the Quaternion product of itself and
     * {@code q1}, (i.e., {@code this = this * q1}).
     * 
     * @param q1
     *          the other Quaternion
     */
    public final void multiply(Quaternion q1) {
            float x, y, w;

            w = this.w * q1.w - this.x * q1.x - this.y * q1.y - this.z * q1.z;
            x = this.w * q1.x + q1.w * this.x + this.y * q1.z - this.z * q1.y;
            y = this.w * q1.y + q1.w * this.y - this.x * q1.z + this.z * q1.x;
            this.z = this.w * q1.z + q1.w * this.z + this.x * q1.y - this.y * q1.x;
            this.w = w;
            this.x = x;
            this.y = y;
    }

    /**
     * Returns the Quaternion which is product of quaternions {@code q1} and
     * {@code q2}.
     * 
     * @param q1
     *          the first Quaternion
     * @param q2
     *          the second Quaternion
     */
    public final static Quaternion multiply(Quaternion q1, Quaternion q2) {
            float x, y, z, w;
            w = q1.w * q2.w - q1.x * q2.x - q1.y * q2.y - q1.z * q2.z;
            x = q1.w * q2.x + q2.w * q1.x + q1.y * q2.z - q1.z * q2.y;
            y = q1.w * q2.y + q2.w * q1.y - q1.x * q2.z + q1.z * q2.x;
            z = q1.w * q2.z + q2.w * q1.z + q1.x * q2.y - q1.y * q2.x;
            return new Quaternion(x, y, z, w);
    }
    /**
     * Multiplies this Quaternion by the inverse of Quaternion {@code q1} and
     * places the value into this Quaternion (i.e., {@code this = this * q^-1}).
     * The value of the argument Quaternion is preserved.
     * 
     * @param q1
     *          the other Quaternion
     */
    public final void multiplyInverse(Quaternion q1) {
            Quaternion tempQuat = new Quaternion(q1);
            tempQuat.invert();
            this.multiply(tempQuat);
    }
	/**
	 *conjugate / set itself to inverse quaternion
	 * */
	public final void conjugate() {
		this.x = -this.x;
		this.y = -this.y;
		this.z = -this.z;
	}/**
     * Utility function that returns the squared norm of the Quaternion.
     */
    public static float squaredNorm(Quaternion q) {
            return (q.x * q.x) + (q.y * q.y) + (q.z * q.z) + (q.w * q.w);
    }
    /**
     * Sets the value of this Quaternion to the inverse of itself.
     */
    public final void invert() {
            this.conjugate();
    }
    
}
