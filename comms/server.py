
import io
import picamera
import logging
import socketserver
import threading
from threading import Condition
from http import server

import json
import socket
import pyaudio
import sched
import time
from signal import pause

from gps import *

PAGE="""\
<html>
<head>
</head>
<body>
<center><img src="stream.mjpg" width="640" height="480"></center>
</body>
</html>
"""
RECORD_GPS_PERIOD_SEC = 1
SAVE_GPS_DATA_FREQ = 10
RECORD_GPS_PRI = 1

class StreamingOutput(object):
    def __init__(self):
        self.frame = None
        self.buffer = io.BytesIO()
        self.condition = Condition()

    def write(self, buf):
        if buf.startswith(b'\xff\xd8'):
            # New frame, copy the existing buffer's content and notify all
            # clients it's available
            self.buffer.truncate()
            with self.condition:
                self.frame = self.buffer.getvalue()
                self.condition.notify_all()
            self.buffer.seek(0)
        return self.buffer.write(buf)

class StreamingHandler(server.BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == '/':
            self.send_response(301)
            self.send_header('Location', '/index.html')
            self.end_headers()
        elif self.path == '/index.html':
            content = PAGE.encode('utf-8')
            self.send_response(200)
            self.send_header('Content-Type', 'text/html')
            self.send_header('Content-Length', len(content))
            self.end_headers()
            self.wfile.write(content)
        elif self.path == '/location_history.json':
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps(location_history).encode(encoding="utf-8"))
        elif self.path == '/stream.mjpg':
            self.send_response(200)
            self.send_header('Age', 0)
            self.send_header('Cache-Control', 'no-cache, private')
            self.send_header('Pragma', 'no-cache')
            self.send_header('Content-Type', 'multipart/x-mixed-replace; boundary=FRAME')
            self.end_headers()
            try:
                while True:
                    with output.condition:
                        output.condition.wait()
                        frame = output.frame
                    self.wfile.write(b'--FRAME\r\n')
                    self.send_header('Content-Type', 'image/jpeg')
                    self.send_header('Content-Length', len(frame))
                    self.end_headers()
                    self.wfile.write(frame)
                    self.wfile.write(b'\r\n')
            except Exception as e:
                logging.warning(
                    'Removed streaming client %s: %s',
                    self.client_address, str(e))
        else:
            self.send_error(404)
            self.end_headers()

class StreamingServer(socketserver.ThreadingMixIn, server.HTTPServer):
    allow_reuse_address = True
    daemon_threads = True


def get_audio():
    ADDRESS = ('', 8765)
    audio = pyaudio.PyAudio()
    CHUNK = 4096
    FORMAT = pyaudio.paInt16
    CHANNELS = 1
    RATE = 44100
    
    stream = audio.open(format=FORMAT, channels=CHANNELS, rate=RATE, output=True,
                        frames_per_buffer=CHUNK)
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as udp_socket:
        udp_socket.bind(ADDRESS)
        try:
            while True:
                data, addr = udp_socket.recvfrom(CHUNK)
                stream.write(data)
        except KeyboardInterrupt:
            print("Killing Audio Server...")
    stream.stop_stream()
    stream.close()
    audio.terminate()


gpsd = gps(mode=WATCH_ENABLE|WATCH_NEWSTYLE)
record_counter = 1
latitude, longitude = None, None
location_history = []


def record_gps_data(scheduler):
    scheduler.enter(RECORD_GPS_PERIOD_SEC, RECORD_GPS_PRI, record_gps_data, (scheduler, ))
    global latitude, longitude, location_history, record_counter
    nx = gpsd.next()
    if nx['class'] == 'TPV':
        latitude = getattr(nx,'lat', None)
        longitude = getattr(nx,'lon', None)
        print(f"Your position: lon={longitude},\tlat={latitude}")
    
    is_data_valid = latitude is not None and longitude is not None
    if record_counter % SAVE_GPS_DATA_FREQ == 0 and is_data_valid:
        location_history.append({"lat": latitude, "lng": longitude})
    record_counter += 1


def start_gps_poller():
    scheduler = sched.scheduler(time.time, time.sleep)
    scheduler.enter(RECORD_GPS_PERIOD_SEC, RECORD_GPS_PRI, record_gps_data, (scheduler, ))
    scheduler.run()
    pause()
    


output = StreamingOutput()


def start_comm_server():
    gps_poller = threading.Thread(target=start_gps_poller)
    gps_poller.start()
    
    #get_audio()
    
    with picamera.PiCamera(resolution='250x250', framerate=12) as camera:
        camera.start_recording(output, format='mjpeg')
        try:
            address = ('', 8000)
            server = StreamingServer(address, StreamingHandler)
            audio_server = threading.Thread(target=get_audio)
            audio_server.start()
            print("Communication server is up")
            server.serve_forever()
        finally:
            camera.stop_recording()
    
