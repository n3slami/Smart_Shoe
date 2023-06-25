import Adafruit_DHT
import simpleaudio
import time
import json

DHT_SENSOR = Adafruit_DHT.DHT22
DATA_PIN = 4
HUMIDITY_THRESHOLD = 10
HUMIDITY_PERIOD_SEC = 30
HUMIDITY_PRI = 1


entering_audio = simpleaudio.WaveObject.from_wave_file("low_freq/audio_files/entering_wet_environment.wav")
exiting_audio = simpleaudio.WaveObject.from_wave_file("low_freq/audio_files/exiting_wet_environment.wav")
alert_state = 0


def get_humidity_and_temprature(scheduler):
	scheduler.enter(HUMIDITY_PERIOD_SEC, HUMIDITY_PRI, get_humidity_and_temprature, (scheduler, ))
	humidity, temprature = Adafruit_DHT.read_retry(DHT_SENSOR, DATA_PIN)
	print(f"{time.ctime()} -- Humidity: {humidity}, Temprature: {temprature}")
	
	global alert_state
	if humidity > HUMIDITY_THRESHOLD:
		if alert_state == 0:
			entering_audio.play()
		alert_state = 1
	else:
		if alert_state == 1:
			exiting_audio.play()
		alert_state = 0


def setup_tasks(scheduler):
	scheduler.enter(HUMIDITY_PERIOD_SEC, HUMIDITY_PRI, get_humidity_and_temprature, (scheduler, ))
