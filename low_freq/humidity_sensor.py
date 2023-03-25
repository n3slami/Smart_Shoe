import Adafruit_DHT
import time
import json

DHT_SENSOR = Adafruit_DHT.DHT22
DATA_PIN = 4
HUMIDITY_PERIOD_SEC = 15
HUMIDITY_PRI = 1


def get_humidity_and_temprature(scheduler):
	scheduler.enter(HUMIDITY_PERIOD_SEC, HUMIDITY_PRI, get_humidity_and_temprature, (scheduler, ))
	humidity, temprature = Adafruit_DHT.read_retry(DHT_SENSOR, DATA_PIN)
	print(f"{time.ctime()} -- Humidity: {humidity}, Temprature: {temprature}")


def setup_tasks(scheduler):
	scheduler.enter(HUMIDITY_PERIOD_SEC, HUMIDITY_PRI, get_humidity_and_temprature, (scheduler, ))
