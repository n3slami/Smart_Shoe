import gpiozero
import RPi.GPIO as GPIO
from multiprocessing import Process
from signal import pause

import high_freq.scheduler
import low_freq.scheduler


def main():
	high_freq_proc = Process(target=high_freq.scheduler.run)
	high_freq_proc.start()
	
	low_freq.scheduler.run()
	
	high_freq_proc.join()

if __name__ == "__main__":
	main()
