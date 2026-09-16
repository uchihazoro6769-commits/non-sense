# Veyra Security Guard — v13

## Maqsad
Veyra zararli fayl va havolalarni foydalanuvchiga yetkazish xavfini kamaytiradi, shu bilan birga E2EE maxfiyligini buzmaslikka harakat qiladi.

## Attachment flow
1. Android client faylni mahalliy tekshiradi.
2. Ijro etiladigan Windows/script turlari bloklanadi.
3. Fayl hajmi 100 MB bilan cheklanadi.
4. APK/arxiv tuzilmasi tekshiriladi va SHA-256 hisoblanadi.
5. Shubhali/unknown APK yuborilishidan oldin ogohlantirish beriladi.
6. Production serverda fayl **quarantine** zonasiga tushishi kerak.
7. Haqiqiy malware engine (ClamAV/YARA/ML va boshqalar) quarantine ichida, alohida sandbox/container/VMda ishlaydi.
8. Faqat `SAFE` verdictdan keyin attachment storagega ko'chiriladi.

## Link flow
Client xavfli sxemalarni (`javascript:`, `data:`, `file:` va h.k.) qabul qilmaydi, userinfo bilan URLlarni shubhali deb belgilaydi va ayrim lokal/suspicious hostlarni ogohlantiradi.

Bu heuristik qatlam **reputatsiya bazasi o'rnini bosmaydi**. Productionda server yoki privacy-preserving URL reputation service qo'shiladi.

## E2EE bilan bog'liqlik
E2EE xabar server tomonidan o'qilmasligi kerak. Shuning uchun server-side link scanning plaintext xabarlarni ko'rishni talab qilmasligi kerak. Attachment scanning esa fayl yuborilishidan oldingi quarantine bosqichida, yoki client-side scanning orqali bajariladi.

## Muhim
Hech qaysi scanner 100% malware aniqlashni kafolatlamaydi. `UNKNOWN` xavfsiz degani emas.
