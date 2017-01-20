package eu.f3rog.incomecounter;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.TextView;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import blade.Blade;
import blade.State;
import rx.Emitter;
import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.internal.util.SubscriptionList;
import rx.schedulers.Schedulers;

@Blade
public final class MainActivity
        extends BaseActivity {

    public static final double DEFAULT_PRICE = 15d;

    @State
    long mStartTime;

    @NonNull
    private SubscriptionList mSubscriptionList = new SubscriptionList();

    @NonNull
    private EditText mHourPriceText;
    @NonNull
    private TextView mIncomeText;
    @NonNull
    private TextView mTimeText;

    @Override
    protected int getLayoutRes() {
        return R.layout.activity_main;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (null == savedInstanceState) {
            mStartTime = System.currentTimeMillis();
        }

        mHourPriceText = (EditText) findViewById(R.id.price);
        mIncomeText = (TextView) findViewById(R.id.income);
        mTimeText = (TextView) findViewById(R.id.time);

        mHourPriceText.setText(String.format(Locale.getDefault(), "%.2f", DEFAULT_PRICE));

        final Subscription subscription = Observable.combineLatest(hoursElapsed(), currentHourlyPrice(),
                new Func2<Double, Double, Double>() {
                    @Override
                    public Double call(@NonNull final Double hours, @NonNull final Double price) {
                        return hours * price;
                    }
                })
                .subscribeOn(Schedulers.io())
                .map(new Func1<Double, String>() {
                    @Override
                    public String call(@NonNull final Double income) {
                        return String.format(Locale.getDefault(), "%.3f€", income);
                    }
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<String>() {
                    @Override
                    public void onCompleted() {
                    }

                    @Override
                    public void onError(Throwable e) {
                        mIncomeText.setText("Error: " + e.getMessage());
                    }

                    @Override
                    public void onNext(@NonNull final String text) {
                        mIncomeText.setText(text);
                    }
                });

        mSubscriptionList.add(subscription);
    }

    private Observable<Double> hoursElapsed() {
        return Observable.interval(100, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.io())
                .map(new Func1<Long, Long>() {
                    @Override
                    public Long call(@NonNull final Long ignored) {
                        final long currentTime = System.currentTimeMillis();
                        return currentTime - mStartTime;
                    }
                })
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext(new Action1<Long>() {
                    @Override
                    public void call(@NonNull final Long ms) {
                        mTimeText.setText(formatInterval(ms));
                    }
                })
                .observeOn(Schedulers.io())
                .map(new Func1<Long, Double>() {
                    @Override
                    public Double call(@NonNull final Long ms) {
                        return (double) ms / 1000d / 60d / 60d;
                    }
                });
    }

    private static String formatInterval(final long ms) {
        final long hr = TimeUnit.MILLISECONDS.toHours(ms);
        final long min = TimeUnit.MILLISECONDS.toMinutes(ms - TimeUnit.HOURS.toMillis(hr));
        final long sec = TimeUnit.MILLISECONDS.toSeconds(ms - TimeUnit.HOURS.toMillis(hr) - TimeUnit.MINUTES.toMillis(min));
        final long restMs = TimeUnit.MILLISECONDS.toMillis(ms - TimeUnit.HOURS.toMillis(hr) - TimeUnit.MINUTES.toMillis(min) - TimeUnit.SECONDS.toMillis(sec));
        return String.format(Locale.getDefault(), "%02d:%02d:%02d.%03d", hr, min, sec, restMs);
    }

    private Observable<Double> currentHourlyPrice() {
        return Observable.fromEmitter(new Action1<Emitter<Double>>() {
            @Override
            public void call(final Emitter<Double> doubleEmitter) {
                doubleEmitter.onNext(getHourPrice());
                mHourPriceText.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    }

                    @Override
                    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    }

                    @Override
                    public void afterTextChanged(Editable editable) {
                        doubleEmitter.onNext(getHourPrice());
                    }
                });
            }
        }, Emitter.BackpressureMode.BUFFER);
    }

    private Double getHourPrice() {
        final String text = mHourPriceText.getText().toString();
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return 0d;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mSubscriptionList.unsubscribe();
    }
}
