import sched
import time
from signal import pause

from .distance_manager import setup_tasks as setup_forward_distance_tasks


def run():
	scheduler = sched.scheduler(time.time, time.sleep)
	setup_forward_distance_tasks(scheduler)
	
	print("High frequency tasks setup done")
	scheduler.run()
	pause()
