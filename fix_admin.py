import bcrypt, subprocess
h = bcrypt.hashpw(b'admin123', bcrypt.gensalt(12)).decode()
print(f"Hash: {h}")
sql = f"UPDATE users SET password_hash = '{h}' WHERE email = 'admin@notifyflow.com';"
subprocess.run(["docker", "exec", "-i", "notifyflow-mysql", "mysql", "-uroot", "-proot", "notifyflow_db"], input=sql, text=True)
result = subprocess.run(["docker", "exec", "-i", "notifyflow-mysql", "mysql", "-uroot", "-proot", "notifyflow_db", "-e", "SELECT email, LEFT(password_hash, 40) FROM users WHERE email = 'admin@notifyflow.com'"], capture_output=True, text=True)
print(result.stdout)
