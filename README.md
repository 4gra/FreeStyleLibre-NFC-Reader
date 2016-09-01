# Development Repository only

Please direct all attention to [vicktor/FreeStyleLibre-NFC-Reader](vicktor/FreeStyleLibre-NFC-Reader)

Obviously all are welcome to use what's here but be aware it might be _significantly_ behind the upstream master, so it's not the upstream authors' responsibility to help with this old branch.  I'll probably abandon this fork and start again if I ever get a new enough Android phone start working on this again.

### SI Units

One notable and unfinished difference (which I should probably form into a pull request) was the conversion to SI units (mmol/l, rather than mg/dl commonly used in the US).  This conversion is based on the molecular weight of glucose and is an approximation of `10/180` just as explained in https://en.wikipedia.org/wiki/Blood_sugar#Units.  My working was something like:

    Using the "quoted" molecular mass of glucose: 180 (g/mol effectively)
     so, moles in 1g of glucose: 1(g) / 180(g/mol) = 0.0055555555[5... recurring] mol/g
     so mmol/mg of glucose will be the same, 0.00555555556 mmol/mg approx.
    1 mg/l = 0.00555555556 mmol/l
     so 1 mg/dl = 10 x 0.00555555556 = 0.0555555556 mmol/l (approx. 10/180)
    
My working used the rounded value of 180 I see stated elsewhere, but I decided to take it back a step further:

    Molecular mass of glucose: 6 x 12.011 (C) + 12 x 1.00794 (H) + 6 x 15.999 (O) 
     = 180.15528 (g/mol effectively) ...
     so 10/180.15528 = 0.055507670938 mmol/l

I then rounded this to 0.05551.  I don't presume to know why 180 is used more commonly than the "precise" figure but given that for any life-sustaining blood reading the difference will always be less than 0.2mmol/l I wasn't too concerned -- I just wanted to use a figure I'd derived myself.

Furthermore, I certainly don't know what values multi-market meter devices use internally to "convert" between results and I wonder if any blood glucose monitoring measures pure C₆H₁₂O₆ or if it's always more complex than that....  Anyway, though it's the least of anyone's concerns with regard to precision or accuracy it's only fair I mention the 'unusual' number.


## Original Docs Follow:
=========================

FreeStyleLibre-NFC-Reader
=========================

Read data from a FreeStyleLibre with Android

Please contribute to this project sending logs and helping to decode the sensor readings.

Contact us at support@socialdiabetes.com www.diabetes.network

See our Wiki in [English](https://github.com/vicktor/FreeStyleLibre-NFC-Reader/wiki) or [Spanish](https://github.com/vicktor/FreeStyleLibre-NFC-Reader/wiki/Inicio)


Contribution
============
Pull requests are welcome!

Feel free to contribute to FreeStyleLibre-NFC-Reader.

If you've fixed a bug or have a feature you've added, just create a pull request.

If you've found a bug, want a new feature, or have other questions, send an [issue](https://github.com/vicktor/FreeStyleLibre-NFC-Reader/issues). We will try to answer asap.
