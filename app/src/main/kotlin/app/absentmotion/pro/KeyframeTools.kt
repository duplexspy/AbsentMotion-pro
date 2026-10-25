package app.absentmotion.pro

import app.absentmotion.model.Keyframe

object KeyframeTools {

    fun reverse(keyframes: List<Keyframe>): List<Keyframe> {
        if (keyframes.size <= 1) return keyframes
        val minT = keyframes.minOf { it.t }
        val maxT = keyframes.maxOf { it.t }
        val reversedValues = keyframes.reversed()

        return keyframes.mapIndexed { index, kf ->
            val corresponding = reversedValues[index]
            Keyframe(
                t = kf.t,
                v = corresponding.v,
                e = corresponding.e,
                gen = corresponding.gen
            )
        }
    }

    fun mirror(keyframes: List<Keyframe>): List<Keyframe> {
        if (keyframes.size <= 1) return keyframes
        val minT = keyframes.minOf { it.t }
        val maxT = keyframes.maxOf { it.t }

        return keyframes.map { kf ->
            val invertedT = minT + (maxT - kf.t)
            Keyframe(
                t = invertedT,
                v = kf.v,
                e = kf.e,
                gen = kf.gen
            )
        }.sortedBy { it.t }
    }

    fun scaleTiming(keyframes: List<Keyframe>, factor: Float): List<Keyframe> {
        if (keyframes.isEmpty() || factor <= 0f) return keyframes
        val firstT = keyframes.first().t

        return keyframes.map { kf ->
            val relative = kf.t - firstT
            val scaled = firstT + relative * factor
            kf.copy(t = scaled.coerceIn(0f, 1f))
        }
    }

    fun easeAll(keyframes: List<Keyframe>, curve: String): List<Keyframe> {
        return keyframes.map { it.copy(e = curve) }
    }

    fun shift(keyframes: List<Keyframe>, deltaT: Float): List<Keyframe> {
        return keyframes.map { kf ->
            kf.copy(t = (kf.t + deltaT).coerceIn(0f, 1f))
        }
    }
}
