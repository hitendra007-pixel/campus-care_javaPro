CAMPUSCARE - JAVA WEB PROJECT

WHAT IS USED AND WHY
1. Java: backend logic for login, complaint validation, department assignment,
   ticket creation, ticket listing, status updates and messaging.
2. Java HttpServer: receives HTTP requests from the browser and serves the website.
3. Java File Handling: stores complaint records in complaints.txt.
4. HTML: website page structure.
5. CSS: website appearance and layout.
6. JavaScript: form actions and HTTP requests to the Java backend.

IMPORTANT JAVA FILE
CampusComplaintSystem.java is the single Java source file. It contains the
backend methods and the built-in Java HTTP server. There is no separate
WebServer.java or Database.java. The project uses text-file storage, not SQL.

STUDENT DEMO ACCOUNTS
AP25110071330 / student123 - Hitendra
AP25110071331 / student123 - Sasank
AP25110071332 / student123 - Lokesh
AP25110071333 / student123 - Srikanta
AP25110071334 / student123 - Abhinav
AP25110071335 / student123 - Prabhas

STAFF DEMO ACCOUNTS
transport / bus123
hostel / host123
itstaff / it123
finance / fee123
general / gen123

ADMIN
admin / admin123

HOW TO RUN
1. Install JDK 17 or later.
2. Open this folder in VS Code.
3. Open the terminal in this folder.
4. Run: javac CampusComplaintSystem.java
5. Run: java CampusComplaintSystem
6. Open http://localhost:8090 in your browser.

On Windows, you can also double-click run.bat.

DATA
Complaints are saved in complaints.txt in this folder. Do not delete this file
if you want to retain submitted complaint records.

NOTE
This is an educational demonstration project. Demo passwords are stored in
source code and the simple request-based role fields are not production-grade
authentication. Do not use real passwords or deploy it publicly as-is.

IMPORTANT FOR LOGIN
Do NOT double-click index.html and do NOT use VS Code Live Server.
Start the project with run.bat (or run the two Java commands above).
The browser must show http://localhost:8090 in its address bar.
Keep the black Java terminal window open while using the website.
If the terminal says "Address already in use", close the older CampusCare
server window first, then run run.bat again.

API CONNECTION FIX
The browser JavaScript now sends API requests directly to
http://localhost:8090/api/ . This means the page can also be previewed
from another local frontend server, but the Java server MUST still be
running on port 8090. Start run.bat first and keep its terminal open.
