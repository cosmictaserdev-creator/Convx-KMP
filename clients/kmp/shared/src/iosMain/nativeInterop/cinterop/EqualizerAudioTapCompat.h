#import <AVFoundation/AVFoundation.h>
#import <AudioToolbox/AudioToolbox.h>
#import <CoreMedia/CoreMedia.h>
#import <MediaToolbox/MediaToolbox.h>
#include <math.h>
#include <stdlib.h>
#include <string.h>

#define CONVX_EQ_MAX_BANDS 20

typedef struct {
    double frequency;
    double gain;
    double q;
    int filterType;
    double b0;
    double b1;
    double b2;
    double a1;
    double a2;
    double x1[2];
    double x2[2];
    double y1[2];
    double y2[2];
    int active;
} ConvxEqualizerBand;

typedef struct {
    double preamp;
    double preampGain;
    int bandCount;
    AudioStreamBasicDescription format;
    ConvxEqualizerBand bands[CONVX_EQ_MAX_BANDS];
} ConvxEqualizerTapState;

static inline void convx_eq_set_coefficients(
    ConvxEqualizerBand *band,
    double b0,
    double b1,
    double b2,
    double a0,
    double a1,
    double a2
) {
    if (!isfinite(a0) || fabs(a0) < 1e-15) {
        band->active = 0;
        return;
    }
    band->b0 = b0 / a0;
    band->b1 = b1 / a0;
    band->b2 = b2 / a0;
    band->a1 = a1 / a0;
    band->a2 = a2 / a0;
    band->active = 1;
}

static inline void convx_eq_prepare_band(ConvxEqualizerBand *band, double sampleRate) {
    if (band->frequency <= 0.0 || band->frequency >= sampleRate / 2.0 || band->q <= 0.0) {
        band->active = 0;
        return;
    }
    if ((band->filterType == 0 || band->filterType == 1 || band->filterType == 2) && band->gain == 0.0) {
        band->active = 0;
        return;
    }

    const double omega = 2.0 * M_PI * band->frequency / sampleRate;
    const double sine = sin(omega);
    const double cosine = cos(omega);
    const double alpha = sine / (2.0 * band->q);
    switch (band->filterType) {
        case 0: {
            const double amplitude = pow(10.0, band->gain / 40.0);
            convx_eq_set_coefficients(
                band,
                1.0 + alpha * amplitude,
                -2.0 * cosine,
                1.0 - alpha * amplitude,
                1.0 + alpha / amplitude,
                -2.0 * cosine,
                1.0 - alpha / amplitude
            );
            break;
        }
        case 1:
        case 2: {
            const double amplitude = pow(10.0, band->gain / 40.0);
            const double shelfAlpha = sine / 2.0 * sqrt(2.0);
            const double aPlusOne = amplitude + 1.0;
            const double aMinusOne = amplitude - 1.0;
            const double twoRootAlpha = 2.0 * sqrt(amplitude) * shelfAlpha;
            if (band->filterType == 1) {
                convx_eq_set_coefficients(
                    band,
                    amplitude * (aPlusOne - aMinusOne * cosine + twoRootAlpha),
                    2.0 * amplitude * (aMinusOne - aPlusOne * cosine),
                    amplitude * (aPlusOne - aMinusOne * cosine - twoRootAlpha),
                    aPlusOne + aMinusOne * cosine + twoRootAlpha,
                    -2.0 * (aMinusOne + aPlusOne * cosine),
                    aPlusOne + aMinusOne * cosine - twoRootAlpha
                );
            } else {
                convx_eq_set_coefficients(
                    band,
                    amplitude * (aPlusOne + aMinusOne * cosine + twoRootAlpha),
                    -2.0 * amplitude * (aMinusOne + aPlusOne * cosine),
                    amplitude * (aPlusOne + aMinusOne * cosine - twoRootAlpha),
                    aPlusOne - aMinusOne * cosine + twoRootAlpha,
                    2.0 * (aMinusOne - aPlusOne * cosine),
                    aPlusOne - aMinusOne * cosine - twoRootAlpha
                );
            }
            break;
        }
        case 3:
            convx_eq_set_coefficients(
                band,
                (1.0 - cosine) / 2.0,
                1.0 - cosine,
                (1.0 - cosine) / 2.0,
                1.0 + alpha,
                -2.0 * cosine,
                1.0 - alpha
            );
            break;
        case 4:
            convx_eq_set_coefficients(
                band,
                (1.0 + cosine) / 2.0,
                -(1.0 + cosine),
                (1.0 + cosine) / 2.0,
                1.0 + alpha,
                -2.0 * cosine,
                1.0 - alpha
            );
            break;
        default:
            band->active = 0;
            break;
    }
}

static inline void convx_eq_tap_init(MTAudioProcessingTapRef tap, void *clientInfo, void **tapStorageOut) {
    (void)tap;
    *tapStorageOut = clientInfo;
}

static inline void convx_eq_tap_finalize(MTAudioProcessingTapRef tap) {
    free(MTAudioProcessingTapGetStorage(tap));
}

static inline void convx_eq_tap_prepare(
    MTAudioProcessingTapRef tap,
    CMItemCount maxFrames,
    const AudioStreamBasicDescription *processingFormat
) {
    (void)maxFrames;
    ConvxEqualizerTapState *state = (ConvxEqualizerTapState *)MTAudioProcessingTapGetStorage(tap);
    if (state == NULL || processingFormat == NULL) return;
    state->format = *processingFormat;
    state->preampGain = pow(10.0, state->preamp / 20.0);
    for (int index = 0; index < state->bandCount; index++) {
        ConvxEqualizerBand *band = &state->bands[index];
        memset(band->x1, 0, sizeof(band->x1));
        memset(band->x2, 0, sizeof(band->x2));
        memset(band->y1, 0, sizeof(band->y1));
        memset(band->y2, 0, sizeof(band->y2));
        convx_eq_prepare_band(band, processingFormat->mSampleRate);
    }
}

static inline void convx_eq_tap_unprepare(MTAudioProcessingTapRef tap) {
    (void)tap;
}

static inline double convx_eq_soft_clip(double sample) {
    const double threshold = 0.9;
    if (sample > threshold) {
        return threshold + (1.0 - threshold) * tanh((sample - threshold) / (1.0 - threshold));
    }
    if (sample < -threshold) {
        return -(threshold + (1.0 - threshold) * tanh((-sample - threshold) / (1.0 - threshold)));
    }
    return sample;
}

static inline double convx_eq_process_sample(ConvxEqualizerTapState *state, int channel, double sample) {
    double output = sample * state->preampGain;
    for (int index = 0; index < state->bandCount; index++) {
        ConvxEqualizerBand *band = &state->bands[index];
        if (!band->active) continue;
        const double filtered = band->b0 * output + band->b1 * band->x1[channel] +
            band->b2 * band->x2[channel] - band->a1 * band->y1[channel] - band->a2 * band->y2[channel];
        band->x2[channel] = band->x1[channel];
        band->x1[channel] = output;
        band->y2[channel] = band->y1[channel];
        band->y1[channel] = filtered;
        output = filtered;
    }
    return isfinite(output) ? convx_eq_soft_clip(output) : 0.0;
}

static inline void convx_eq_tap_process(
    MTAudioProcessingTapRef tap,
    CMItemCount numberFrames,
    MTAudioProcessingTapFlags flags,
    AudioBufferList *bufferListInOut,
    CMItemCount *numberFramesOut,
    MTAudioProcessingTapFlags *flagsOut
) {
    (void)flags;
    CMTimeRange sourceTimeRange;
    const OSStatus status = MTAudioProcessingTapGetSourceAudio(
        tap,
        numberFrames,
        bufferListInOut,
        flagsOut,
        numberFramesOut,
        &sourceTimeRange
    );
    if (status != noErr || bufferListInOut == NULL || numberFramesOut == NULL) return;

    ConvxEqualizerTapState *state = (ConvxEqualizerTapState *)MTAudioProcessingTapGetStorage(tap);
    if (state == NULL || state->format.mChannelsPerFrame == 0 || state->format.mChannelsPerFrame > 2) return;
    const AudioStreamBasicDescription format = state->format;
    const int nonInterleaved = (format.mFormatFlags & kAudioFormatFlagIsNonInterleaved) != 0;
    const int isFloat = (format.mFormatFlags & kAudioFormatFlagIsFloat) != 0;
    const int isSignedInteger = (format.mFormatFlags & kAudioFormatFlagIsSignedInteger) != 0;
    if (format.mFormatID != kAudioFormatLinearPCM || (!nonInterleaved && format.mChannelsPerFrame > 2)) return;
    if (!((isFloat && format.mBitsPerChannel == 32) || (isSignedInteger && format.mBitsPerChannel == 16))) return;

    for (UInt32 bufferIndex = 0; bufferIndex < bufferListInOut->mNumberBuffers; bufferIndex++) {
        AudioBuffer *audioBuffer = &bufferListInOut->mBuffers[bufferIndex];
        const int channelsInBuffer = nonInterleaved ? 1 : (int)audioBuffer->mNumberChannels;
        const int firstChannel = nonInterleaved ? (int)bufferIndex : 0;
        if (channelsInBuffer <= 0 || firstChannel >= 2 || audioBuffer->mData == NULL) continue;
        if (isFloat) {
            Float32 *samples = (Float32 *)audioBuffer->mData;
            const size_t requestedSamples = (size_t)(*numberFramesOut) * (size_t)channelsInBuffer;
            const size_t availableSamples = audioBuffer->mDataByteSize / sizeof(Float32);
            const size_t sampleCount = requestedSamples < availableSamples ? requestedSamples : availableSamples;
            for (size_t sampleIndex = 0; sampleIndex < sampleCount; sampleIndex++) {
                const int channel = nonInterleaved ? firstChannel : (int)(sampleIndex % (size_t)channelsInBuffer);
                samples[sampleIndex] = (Float32)convx_eq_process_sample(state, channel, samples[sampleIndex]);
            }
        } else {
            SInt16 *samples = (SInt16 *)audioBuffer->mData;
            const size_t requestedSamples = (size_t)(*numberFramesOut) * (size_t)channelsInBuffer;
            const size_t availableSamples = audioBuffer->mDataByteSize / sizeof(SInt16);
            const size_t sampleCount = requestedSamples < availableSamples ? requestedSamples : availableSamples;
            for (size_t sampleIndex = 0; sampleIndex < sampleCount; sampleIndex++) {
                const int channel = nonInterleaved ? firstChannel : (int)(sampleIndex % (size_t)channelsInBuffer);
                const double input = samples[sampleIndex] / 32768.0;
                samples[sampleIndex] = (SInt16)lrint(convx_eq_process_sample(state, channel, input) * 32767.0);
            }
        }
    }
}

static inline int convx_av_player_set_equalizer(
    id player,
    int enabled,
    double preamp,
    const double *frequencies,
    const double *gains,
    const double *qualities,
    const int *filterTypes,
    int bandCount
) {
    AVPlayerItem *item = [(AVPlayer *)player currentItem];
    if (item == nil) return 0;
    if (!enabled || (bandCount <= 0 && preamp == 0.0)) {
        item.audioMix = nil;
        return 1;
    }

    NSArray *audioTracks = [item.asset tracksWithMediaType:AVMediaTypeAudio];
    if (audioTracks.count == 0) return 0;
    NSMutableArray *inputParameters = [NSMutableArray arrayWithCapacity:audioTracks.count];
    MTAudioProcessingTapCallbacks callbacks = {0};
    callbacks.version = kMTAudioProcessingTapCallbacksVersion_0;
    callbacks.init = convx_eq_tap_init;
    callbacks.finalize = convx_eq_tap_finalize;
    callbacks.prepare = convx_eq_tap_prepare;
    callbacks.unprepare = convx_eq_tap_unprepare;
    callbacks.process = convx_eq_tap_process;

    for (AVAssetTrack *track in audioTracks) {
        ConvxEqualizerTapState *state = (ConvxEqualizerTapState *)calloc(1, sizeof(ConvxEqualizerTapState));
        if (state == NULL) continue;
        state->preamp = preamp;
        state->bandCount = bandCount > CONVX_EQ_MAX_BANDS ? CONVX_EQ_MAX_BANDS : bandCount;
        for (int index = 0; index < state->bandCount; index++) {
            state->bands[index].frequency = frequencies[index];
            state->bands[index].gain = gains[index];
            state->bands[index].q = qualities[index];
            state->bands[index].filterType = filterTypes[index];
        }
        callbacks.clientInfo = state;

        MTAudioProcessingTapRef tap = NULL;
        const OSStatus tapStatus = MTAudioProcessingTapCreate(
            kCFAllocatorDefault,
            &callbacks,
            kMTAudioProcessingTapCreationFlag_PreEffects,
            &tap
        );
        if (tapStatus != noErr || tap == NULL) {
            free(state);
            continue;
        }

        AVMutableAudioMixInputParameters *parameters =
            [AVMutableAudioMixInputParameters audioMixInputParametersWithTrack:track];
        parameters.audioTapProcessor = tap;
        [inputParameters addObject:parameters];
        CFRelease(tap);
    }

    if (inputParameters.count == 0) return 0;
    AVMutableAudioMix *audioMix = [AVMutableAudioMix audioMix];
    audioMix.inputParameters = inputParameters;
    item.audioMix = audioMix;
    return 1;
}
