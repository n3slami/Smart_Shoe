import sched
import time

from .humidity_sensor import setup_tasks as setup_humidity_checking_tasks


def run():
	scheduler = sched.scheduler(time.time, time.sleep)
	setup_humidity_checking_tasks(scheduler)
	
	print("Low frequency tasks setup done")
	scheduler.run()
	
