import bcrypt

def hash_password(password: str) -> str:
    # 문자열을 바이트로 변환하고 솔트(salt)를 추가해 해싱합니다.
    pwd_bytes = password.encode('utf-8')
    salt = bcrypt.gensalt()
    hashed_password = bcrypt.hashpw(pwd_bytes, salt)
    
    # DB에 문자열(VARCHAR) 형태로 저장하기 위해 다시 디코딩해서 반환합니다.
    return hashed_password.decode('utf-8')

def verify_password(plain_password: str, hashed_password: str) -> bool:
    # 입력받은 평문 비밀번호와 DB에 저장된 해시 비밀번호를 모두 바이트로 변환합니다.
    password_byte_enc = plain_password.encode('utf-8')
    hashed_password_byte_enc = hashed_password.encode('utf-8')
    
    # bcrypt.checkpw를 사용해 일치 여부를 검증합니다.
    return bcrypt.checkpw(password_byte_enc, hashed_password_byte_enc)