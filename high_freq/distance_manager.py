from gpiozero import DistanceSensor
import numpy as np
import simpleaudio
import time
import json

TRIG_PIN, ECHO_PIN = 23, 24
DIST_M_MAX = 2
TO_CM_SCALING = 100

UPDATE_ALERT_PERIOD_SEC = 0.5
UPDATE_ALERT_PRI = 3
update_conditions = None

ALERT_PERIOD_SEC = 1
ALERT_PRI = 2
alert_mode = 0
ALERT_SAMPLING_RATE = 44100
audio_waves = []


def alert_user(scheduler):
	scheduler.enter(ALERT_PERIOD_SEC, ALERT_PRI, alert_user, (scheduler, ))
	# print(f"ALERT TYPE {alert_mode}! @ {time.ctime()}")
	simpleaudio.play_buffer(audio_waves[alert_mode], 1, 2, ALERT_SAMPLING_RATE)


def update_alert_mode(scheduler, distance_sensor):
	scheduler.enter(UPDATE_ALERT_PERIOD_SEC, UPDATE_ALERT_PRI, 
					update_alert_mode, (scheduler, distance_sensor, ))
	
	global alert_mode
	if distance_sensor.value == 1:
		alert_mode = None
		return
	reading = distance_sensor.distance * TO_CM_SCALING
	for mode, cond in enumerate(update_conditions):
		if reading <= cond["threshold"]:
			alert_mode = mode
			break


def setup_audio_tracks():
	global audio_waves
	for cond in update_conditions:
		T = cond["duration"]
		# Setup waveform according to the sound config
		times = np.linspace(0, T, int(T * ALERT_SAMPLING_RATE), False)
		wave = np.sin(cond["frequency"] * 2 * np.pi * times)
		# Normalize to 16-bit range
		wave *= 32767 / np.max(np.abs(wave))
		wave = wave.astype(np.int64)
		# Save waveform
		audio_waves.append(wave)


def setup_tasks(scheduler):
	distance_sensor = DistanceSensor(trigger=TRIG_PIN, echo=ECHO_PIN, max_distance=DIST_M_MAX)
	global update_conditions
	with open("config/alert.json", 'r') as config_file:
		update_conditions = json.load(config_file)
	setup_audio_tracks()
	
	scheduler.enter(ALERT_PERIOD_SEC, ALERT_PRI, alert_user, (scheduler, ))
	scheduler.enter(UPDATE_ALERT_PERIOD_SEC, UPDATE_ALERT_PRI, 
					update_alert_mode, (scheduler, distance_sensor, ))
